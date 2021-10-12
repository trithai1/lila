package lila.sense

import org.joda.time.DateTime
import lila.common.CuteNameGenerator
import lila.user.User

case class SensePlayer(id: SensePlayer.Id, createdAt: DateTime, score: Int) {

  import SensePlayer.Id

  lazy val userId: Option[User.ID] = id match {
    case Id.User(name) => User.normalize(name).some
    case _             => none
  }

  lazy val name: String = id match {
    case Id.User(n)  => n
    case Id.Anon(id) => CuteNameGenerator fromSeed id.hashCode
  }
}

object SensePlayer {
  sealed trait Id
  object Id {
    case class User(name: String)      extends Id
    case class Anon(sessionId: String) extends Id
    def apply(str: String) =
      if (str startsWith "@") Anon(str drop 1)
      else User(str)
  }

  val lichess = Id.User("Lichess")

  def make(id: Id) = SensePlayer(id = id, score = 0, createdAt = DateTime.now)
}
