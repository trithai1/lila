package lila.sense

import com.softwaremill.macwire._

import lila.common.LightUser
import lila.db.AsyncColl
import lila.storm.StormJson
import lila.storm.StormSelector
import lila.storm.StormSign

@Module
final class Env(
    selector: StormSelector,
    puzzleColls: lila.puzzle.PuzzleColls,
    cacheApi: lila.memo.CacheApi,
    stormJson: StormJson,
    stormSign: StormSign,
    userRepo: lila.user.UserRepo,
    lightUserGetter: LightUser.GetterSync,
    remoteSocketApi: lila.socket.RemoteSocket,
    db: lila.db.Db
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    mode: play.api.Mode
) {

  private lazy val colls = new SenseColls(puzzle = puzzleColls.puzzle)

  lazy val api = wire[SenseApi]

  lazy val lobby = wire[SenseLobby]

  lazy val json = wire[SenseJson]

  private val socket = wire[SenseSocket] // requires eager eval
}

final private class SenseColls(val puzzle: AsyncColl)
