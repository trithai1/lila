package lila.sense

import lila.room.RoomSocket.{ Protocol => RP, _ }
import lila.socket.RemoteSocket.{ Protocol => P, _ }
import play.api.libs.json.{ JsObject, Json }

final private class SenseSocket(
    api: SenseApi,
    json: SenseJson,
    remoteSocketApi: lila.socket.RemoteSocket
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mode: play.api.Mode
) {

  import SenseSocket._

  def publishState(race: SenseRace): Unit = send(
    Protocol.Out.publishState(race.id, json state race)
  )

  private lazy val send: String => Unit = remoteSocketApi.makeSender("sense-out").apply _

  lazy val rooms = makeRoomMap(send)

  private lazy val senseHandler: Handler = {
    case Protocol.In.PlayerJoin(raceId, playerId) =>
      api.join(raceId, playerId).unit
    case Protocol.In.PlayerScore(raceId, playerId, score) =>
      api.registerPlayerScore(raceId, playerId, score)
    case Protocol.In.RaceStart(raceId, playerId) =>
      api
        .get(raceId)
        .filter(_.startsAt.isEmpty)
        .filter(_.owner == playerId)
        .foreach(api.manualStart)
  }

  remoteSocketApi.subscribe("sense-in", Protocol.In.reader)(
    senseHandler orElse minRoomHandler(rooms, logger) orElse remoteSocketApi.baseHandler
  )

  api registerSocket this
}

object SenseSocket {

  object Protocol {

    object In {

      case class PlayerJoin(race: SenseRace.Id, player: SensePlayer.Id)              extends P.In
      case class PlayerScore(race: SenseRace.Id, player: SensePlayer.Id, score: Int) extends P.In
      case class RaceStart(race: SenseRace.Id, player: SensePlayer.Id)               extends P.In

      val reader: P.In.Reader = raw => raceReader(raw) orElse RP.In.reader(raw)

      val raceReader: P.In.Reader = raw =>
        raw.path match {
          case "sense/join" =>
            raw.get(2) { case Array(raceId, playerId) =>
              PlayerJoin(SenseRace.Id(raceId), SensePlayer.Id(playerId)).some
            }
          case "sense/score" =>
            raw.get(3) { case Array(raceId, playerId, scoreStr) =>
              scoreStr.toIntOption map { PlayerScore(SenseRace.Id(raceId), SensePlayer.Id(playerId), _) }
            }
          case "sense/start" =>
            raw.get(2) { case Array(raceId, playerId) =>
              RaceStart(SenseRace.Id(raceId), SensePlayer.Id(playerId)).some
            }
          case _ => none
        }
    }

    object Out {

      def publishState(id: SenseRace.Id, data: JsObject) = s"sense/state $id ${Json stringify data}"
    }
  }
}
