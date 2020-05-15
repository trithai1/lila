package lila.swiss

import akka.stream.scaladsl._
import akka.util.ByteString
import java.io.File
import scala.concurrent.blocking
import scala.sys.process._

import lila.user.User

final private class PairingSystem(trf: SwissTrf, rankingApi: SwissRankingApi, executable: String)(implicit
    ec: scala.concurrent.ExecutionContext,
    mat: akka.stream.Materializer
) {

  def apply(swiss: Swiss): Fu[List[SwissPairing.ByeOrPending]] =
    rankingApi(swiss) flatMap { ranking =>
      invoke(swiss, trf(swiss, ranking)) map reader(ranking.map(_.swap))
    }

  private def invoke(swiss: Swiss, input: Source[String, _]): Fu[List[String]] =
    withTempFile(input) { file =>
      val command = s"$executable --dutch $file -p"
      val stdout  = new collection.mutable.ListBuffer[String]
      val stderr  = new StringBuilder
      val status = lila.common.Chronometer.syncMon(_.swiss.bbpairing) {
        blocking {
          command ! ProcessLogger(stdout append _, stderr append _)
        }
      }
      if (status != 0) {
        val error = stderr.toString
        if (error contains "No valid pairing exists") Nil
        else throw new PairingSystem.BBPairingException(error, swiss)
      } else stdout.toList
    }

  private def reader(rankingSwap: Map[Int, User.ID])(output: List[String]): List[SwissPairing.ByeOrPending] =
    output
      .drop(1) // first line is the number of pairings
      .map(_ split ' ')
      .collect {
        case Array(p, "0") =>
          p.toIntOption flatMap rankingSwap.get map { userId =>
            Left(SwissPairing.Bye(userId))
          }
        case Array(w, b) =>
          for {
            white <- w.toIntOption flatMap rankingSwap.get
            black <- b.toIntOption flatMap rankingSwap.get
          } yield Right(SwissPairing.Pending(white, black))
      }
      .flatten

  def withTempFile[A](contents: Source[String, _])(f: File => A): Fu[A] = {
    // NOTE: The prefix and suffix must be at least 3 characters long,
    // otherwise this function throws an IllegalArgumentException.
    val file = File.createTempFile("lila-", "-swiss")
    contents
      .intersperse("\n")
      .map(ByteString.apply)
      .runWith(FileIO.toPath(file.toPath))
      .map { _ =>
        val res = f(file)
        file.delete()
        res
      }
  }
}

private object PairingSystem {
  case class BBPairingException(val message: String, val swiss: Swiss) extends lila.base.LilaException
}
