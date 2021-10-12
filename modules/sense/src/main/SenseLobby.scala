package lila.sense

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

final class SenseLobby(api: SenseApi)(implicit ec: ExecutionContext, system: akka.actor.ActorSystem) {

  def join(player: SensePlayer.Id): Fu[SenseRace.Id] = workQueue {
    currentRace flatMap {
      case race if race.players.sizeIs >= SenseRace.maxPlayers => makeNewRace(7)
      case race if race.startsInMillis.exists(_ < 3000)        => makeNewRace(10)
      case race                                                => fuccess(race.id)
    } map { raceId =>
      api.join(raceId, player)
      raceId
    }
  }

  private val workQueue =
    new lila.hub.AsyncActorSequencer(
      maxSize = 128,
      timeout = 20 seconds,
      name = "sense.lobby"
    )

  private val fallbackRace = SenseRace.make(SensePlayer.lichess, Nil, 10)

  private var currentId: Fu[SenseRace.Id] = api.create(SensePlayer.lichess, 10)

  private def currentRace: Fu[SenseRace] = currentId.map(api.get) dmap { _ | fallbackRace }

  private def makeNewRace(countdownSeconds: Int): Fu[SenseRace.Id] = {
    currentId = api.create(SensePlayer.lichess, countdownSeconds)
    currentId
  }
}
