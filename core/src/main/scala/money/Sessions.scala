package money

import scala.collection.parallel.ParMap
import scala.util.Random
import money.db.Id
import money.models.User
import money.errors._

trait Sessions {
  self: MoneyService =>

  private var sessions = ParMap.empty[String, Id[User]]

  private def newSession(userId: Id[User]): Result[String] = {
    val key = Random.alphanumeric.take(32).mkString
    if (sessions contains key) newSession(userId)
    //else if (isLoggedIn(userId)) Bad(s"User $userId is already logged in")
    else Good(key)
  }

  protected def findLogin(session: String): Result[Id[User]] =
    option2Result(sessions.get(session)) toResult MiscError(s"Session expired")

  protected def isLoggedIn(userId: Id[User]): Boolean =
    sessions.values exists (_ == userId)

  protected def reverseLookup(userId: Id[User]): Result[String] =
    option2Result(sessions find {
      case (k, v) => v == userId
    } map (_._1)) toResult MiscError(s"User $userId is not logged in")

  protected def createSession(userId: Id[User]): Result[String] = newSession(userId) map { s =>
    sessions += (s -> userId)
    s
  }

  protected def deleteSession(userId: Id[User]): Result[Unit] =
    reverseLookup(userId) map (s => sessions -= s)
}