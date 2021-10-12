package lila.sense

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import lila.memo.CacheApi
import lila.storm.StormSelector
import lila.user.{ User, UserRepo }
import lila.common.Bus

final class SenseApi(colls: SenseColls, selector: StormSelector, userRepo: UserRepo, cacheApi: CacheApi)(
    implicit
    ec: ExecutionContext,
    system: akka.actor.ActorSystem
) {

  import SenseRace.Id

  private val store = cacheApi.notLoadingSync[SenseRace.Id, SenseRace](2048, "sense.race")(
    _.expireAfterAccess(30 minutes).build()
  )

  def get(id: Id): Option[SenseRace] = store getIfPresent id

  def playerId(sessionId: String, user: Option[User]) = user match {
    case Some(u) => SensePlayer.Id.User(u.id)
    case None    => SensePlayer.Id.Anon(sessionId)
  }

  def createAndJoin(player: SensePlayer.Id): Fu[SenseRace.Id] =
    create(player, 10).map { id =>
      join(id, player)
      id
    }

  def create(player: SensePlayer.Id, countdownSeconds: Int): Fu[SenseRace.Id] =
    selector.apply map { puzzles =>
      val race = SenseRace
        .make(
          owner = player,
          puzzles = puzzles.grouped(2).flatMap(_.headOption).toList,
          countdownSeconds = 5
        )
      store.put(race.id, race)
      lila.mon.sense.race(lobby = race.isLobby).increment()
      race.id
    }

  private val rematchQueue =
    new lila.hub.AsyncActorSequencer(
      maxSize = 32,
      timeout = 20 seconds,
      name = "sense.rematch"
    )

  def rematch(race: SenseRace, player: SensePlayer.Id): Fu[SenseRace.Id] = race.rematch.flatMap(get) match {
    case Some(found) if found.finished => rematch(found, player)
    case Some(found) =>
      join(found.id, player)
      fuccess(found.id)
    case None =>
      rematchQueue {
        createAndJoin(player) map { rematchId =>
          save(race.copy(rematch = rematchId.some))
          rematchId
        }
      }
  }

  def join(id: SenseRace.Id, player: SensePlayer.Id): Option[SenseRace] =
    get(id).flatMap(_ join player) map { r =>
      val race = (r.isLobby ?? doStart(r)) | r
      saveAndPublish(race)
      race
    }

  private[sense] def manualStart(race: SenseRace): Unit = !race.isLobby ?? {
    doStart(race) foreach saveAndPublish
  }

  private def doStart(race: SenseRace): Option[SenseRace] =
    race.startCountdown.map { starting =>
      system.scheduler.scheduleOnce(SenseRace.duration.seconds + race.countdownSeconds.seconds + 50.millis) {
        finish(race.id)
      }
      starting
    }

  private def finish(id: SenseRace.Id): Unit =
    get(id) foreach { race =>
      lila.mon.sense.players(lobby = race.isLobby).record(race.players.size)
      race.players foreach { player =>
        lila.mon.sense.score(lobby = race.isLobby, auth = player.userId.isDefined).record(player.score)
        player.userId.ifTrue(player.score > 0) foreach { userId =>
          Bus.publish(lila.hub.actorApi.puzzle.SenseRun(userId, player.score), "senseRun")
          userRepo.addSenseRun(userId, player.score)
        }
      }
      publish(race)
    }

  def registerPlayerScore(id: SenseRace.Id, player: SensePlayer.Id, score: Int): Unit = {
    if (score >= 130) logger.warn(s"$id $player score: $score")
    else get(id).flatMap(_.registerScore(player, score)) foreach saveAndPublish
  }

  private def save(race: SenseRace): Unit =
    store.put(race.id, race)

  private def saveAndPublish(race: SenseRace): Unit = {
    save(race)
    publish(race)
  }
  private def publish(race: SenseRace): Unit = {
    socket.foreach(_ publishState race)
  }

  // work around circular dependency
  private var socket: Option[SenseSocket] = None
  private[sense] def registerSocket(s: SenseSocket) = { socket = s.some }
}
