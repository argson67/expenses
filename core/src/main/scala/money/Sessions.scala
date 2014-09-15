package money

import akka.actor.{Props, ActorSystem}

import scala.collection.parallel.ParMap
import scala.util.Random
import money.db.Id
import money.models.User
import money.errors._
import org.joda.time.DateTime

trait Sessions {
  self: MoneyService =>

  private var sessions = ParMap.empty[String, (DateTime, Id[User])]

  private def newSession(userId: Id[User]): Result[String] = {
    val key = Random.alphanumeric.take(32).mkString
    if (sessions contains key) newSession(userId)
    //else if (isLoggedIn(userId)) Bad(s"User $userId is already logged in")
    else Good(key)
  }

  protected def findLogin(session: String): Result[Id[User]] =
    option2Result(sessions.get(session)) toResult MiscError(s"Session expired") map (_._2)

  protected def isLoggedIn(userId: Id[User]): Boolean =
    sessions.values exists (_._2 == userId)

  protected def reverseLookup(userId: Id[User]): Result[String] =
    option2Result(sessions find {
      case (k, v) => v._2 == userId
    } map (_._1)) toResult MiscError(s"User $userId is not logged in")

  protected def createSession(userId: Id[User], expirationDate: DateTime): Result[String] = newSession(userId) map { s =>
    sessions += (s -> (expirationDate, userId))
    s
  }

  protected def deleteSession(userId: Id[User]): Result[Unit] =
    reverseLookup(userId) map (s => sessions -= s)

  protected def checkExpiredSessions(): Unit = {
    println("Checking for expired sessions")

    sessions.toMap foreach { s =>
      val (id, (expirationDate, _)) = s
      val now = DateTime.now()
      if (now.isAfter(expirationDate)) {
        sessions -= id
      }
    }
  }
}