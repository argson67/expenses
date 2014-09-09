// http://eng.42go.com/scala-slick-database-drivers-type-safing-session-concurrency/

package money.db

import money.errors._

// Java imports
import java.sql.SQLException

// Scala imports
import scala.concurrent._
import ExecutionContext.Implicits.global // TODO: Figure this out

// Slick imports
import scala.slick.driver.PostgresDriver.simple.{ Database => SlickDatabase, Session}
import scala.slick.jdbc.ResultSetConcurrency
import scala.collection.mutable

// Local imports
import DBSession._

trait SlickSessionProvider {
  def getReadOnlySession(handle: SlickDatabase): ROSession
  def getReadWriteSession(handle: SlickDatabase): RWSession
  def recycleReadOnlySession(s: ROSession): Unit
  def recycleReadWriteSession(s: RWSession): Unit
  def closeRW(): Unit
  def closeRO(): Unit
  def closeAll(): Unit
}

class SlickSessionProviderImpl extends SlickSessionProvider /*with Logger*/ {
  private val roSessions = mutable.Stack[ROSession]()
  private val rwSessions = mutable.Stack[RWSession]()

  def getReadOnlySession(handle: SlickDatabase): ROSession = synchronized {
    if (roSessions.isEmpty) {
      //info("Creating new ROSession")
      new ROSession(handle.createSession().forParameters(rsConcurrency = ResultSetConcurrency.ReadOnly))
    } else {
      //info("Reusing ROSession")
      roSessions.pop()
    }
  }

  def recycleReadOnlySession(s: ROSession): Unit = synchronized {
    roSessions.push(s)
  }

  def getReadWriteSession(handle: SlickDatabase): RWSession = synchronized {
    if (rwSessions.isEmpty) {
      //info("Creating new RWSession")
      new RWSession(handle.createSession().forParameters(rsConcurrency = ResultSetConcurrency.Updatable))
    } else {
      //info("Reusing RWSession")
      rwSessions.pop()
    }
  }

  def recycleReadWriteSession(s: RWSession): Unit = synchronized {
    rwSessions.push(s)
  }

  def closeRW(): Unit = synchronized {
    rwSessions foreach (_.close())
    rwSessions.clear()
  }

  def closeRO(): Unit = synchronized {
    roSessions foreach (_.close())
    roSessions.clear()
  }

  def closeAll(): Unit = {
    closeRO()
    closeRW()
  }
}

class Database(val db: SlickDatabase, val sessionProvider: SlickSessionProvider = new SlickSessionProviderImpl) /*extends Logger*/ {
  def readOnlyAsync[T](f: ROSession => T): Future[T] = future {
    readOnly(f)
  }

  def readWriteAsync[T](f: RWSession => Result[T]): Future[Result[T]] = future {
    readWrite(f)
  }

  def readWriteAsync[T](attempts: Int)(f: RWSession => Result[T]): Future[Result[T]] = future {
    readWrite(attempts)(f)
  }

  def readOnly[T](f: ROSession => T): T = {
    val s = sessionProvider.getReadOnlySession(db)
    try {
      f(s)
    } finally sessionProvider.recycleReadOnlySession(s)
  }

  def readWrite[T](f: RWSession => Result[T]): Result[T] = {
    val s = sessionProvider.getReadWriteSession(db)
    try {
      s.withTransaction {
        f(s) match {
          case Good(res) => Good(res)
          case Bad(err)  =>
            s.rollback()
            Bad(err)
        }
      }
    } finally sessionProvider.recycleReadWriteSession(s)
  }

  def readWrite[T](attempts: Int)(f: RWSession => Result[T]): Result[T] = {
    1 to attempts - 1 foreach {
      attempt =>
        try {
          return readWrite(f)
        } catch {
          case psqlE: org.postgresql.util.PSQLException =>
            Bad(DBError(psqlE.getMessage))
            //error(s"Error: $psqlE")
          case t: SQLException =>
            Bad(DBError(t.getMessage))
        }
    }
    readWrite(f)
  }

  def cleanUp(): Unit = sessionProvider.closeAll()
}
