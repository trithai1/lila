package lila.sense

import play.api.libs.json._

import lila.common.LightUser
import lila.storm.StormJson
import lila.storm.StormSign

final class SenseJson(stormJson: StormJson, sign: StormSign, lightUserSync: LightUser.GetterSync) {

  import StormJson._

  implicit private val playerWrites = OWrites[SensePlayer] { p =>
    val user = p.userId flatMap lightUserSync
    Json
      .obj("name" -> p.name, "score" -> p.score)
      .add("userId", p.userId)
      .add("title", user.flatMap(_.title))
  }

  // full race data
  def data(race: SenseRace, player: SensePlayer) =
    Json
      .obj(
        "race" -> Json
          .obj("id" -> race.id.value)
          .add("lobby", race.isLobby),
        "player"  -> player,
        "puzzles" -> race.puzzles
      )
      .add("owner", race.owner == player.id) ++ state(race)

  // socket updates
  def state(race: SenseRace) = Json
    .obj(
      "players" -> race.players
    )
    .add("startsIn", race.startsInMillis)
}
