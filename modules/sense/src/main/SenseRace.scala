package lila.sense

import org.joda.time.DateTime

import lila.storm.StormPuzzle

case class SenseRace(
    _id: SenseRace.Id,
    owner: SensePlayer.Id,
    players: List[SensePlayer],
    puzzles: List[StormPuzzle],
    countdownSeconds: Int,
    startsAt: Option[DateTime],
    rematch: Option[SenseRace.Id]
) {

  def id = _id

  def has(id: SensePlayer.Id) = players.exists(_.id == id)

  def player(id: SensePlayer.Id) = players.find(_.id == id)

  def join(id: SensePlayer.Id): Option[SenseRace] =
    !hasStarted && !has(id) && players.sizeIs < SenseRace.maxPlayers option
      copy(players = players :+ SensePlayer.make(id))

  def registerScore(playerId: SensePlayer.Id, score: Int): Option[SenseRace] =
    !finished option copy(
      players = players map {
        case p if p.id == playerId => p.copy(score = score)
        case p                     => p
      }
    )

  def startCountdown: Option[SenseRace] =
    startsAt.isEmpty && players.size > (if (isLobby) 2 else 1) option
      copy(startsAt = DateTime.now.plusSeconds(countdownSeconds).some)

  def startsInMillis = startsAt.map(d => d.getMillis - nowMillis)

  def hasStarted = startsInMillis.exists(_ <= 0)

  def finishesAt = startsAt.map(_ plusSeconds SenseRace.duration)

  def finished = finishesAt.exists(_.isBeforeNow)

  def isLobby = owner == SensePlayer.lichess
}

object SenseRace {

  val duration   = 90
  val maxPlayers = 10

  case class Id(value: String) extends AnyVal with StringValue

  def make(owner: SensePlayer.Id, puzzles: List[StormPuzzle], countdownSeconds: Int) = SenseRace(
    _id = Id(lila.common.ThreadLocalRandom nextString 5),
    owner = owner,
    players = Nil,
    puzzles = puzzles,
    countdownSeconds = countdownSeconds,
    startsAt = none,
    rematch = none
  )
}
