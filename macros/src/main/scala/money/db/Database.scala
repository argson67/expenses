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
}

class SlickSessionProviderImpl extends SlickSessionProvider /*with Logger*/ {
  def getReadOnlySession(handle: SlickDatabase): ROSession = synchronized {
    /*if (roSessions.isEmpty) {
      //info("Creating new ROSession")
      new ROSession(handle.createSession().forParameters(rsConcurrency = ResultSetConcurrency.ReadOnly))
    } else {
      //info("Reusing ROSession")
      roSessions.pop()
    }*/

    new ROSession(handle.createSession().forParameters(rsConcurrency = ResultSetConcurrency.ReadOnly))
  }

  def getReadWriteSession(handle: SlickDatabase): RWSession = synchronized {
    /*if (rwSessions.isEmpty) {
      //info("Creating new RWSession")
      new RWSession(handle.createSession().forParameters(rsConcurrency = ResultSetConcurrency.Updatable))
    } else {
      //info("Reusing RWSession")
      rwSessions.pop()
    }*/

    new RWSession(handle.createSession().forParameters(rsConcurrency = ResultSetConcurrency.Updatable))
  }
}

import com.mchange.v2.c3p0.ComboPooledDataSource

class Database(val db: SlickDatabase, val ds: ComboPooledDataSource, val sessionProvider: SlickSessionProvider = new SlickSessionProviderImpl) /*extends Logger*/ {
  def stats: Unit = {
    println(s"# connections: ${ds.getNumConnections}")
    println(s"# busy connections: ${ds.getNumBusyConnections}")
    println(s"# idle connections: ${ds.getNumIdleConnections}")
  }

  def readOnlyAsync[T](f: ROSession => T): Future[T] = future {
    readOnly(f)
  }

  def readWriteAsync[T](f: RWSession => Result[T]): Future[Result[T]] = future {
    readWrite(f)
  }

  def readWriteAsync[T](attempts: Int)(f: RWSession => Result[T]): Future[Result[T]] = future {
    readWrite(attempts)(f)
  }

  private def timer[R](name: String)(block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    val ms = (t1 - t0) / 1000000
    println(s"Elapsed time in '$name': $ms ms")
    result
  }

  def readOnly[T](f: ROSession => T): T = {
    val s = timer("getROSession")(sessionProvider.getReadOnlySession(db))
    timer("readWrite") {
      try {
        val res = f(s)
        //stats
        //println(s"readonly, result: $res")
        res
      } finally {
        s.close()
      }
    }
  }

  def readWrite[T](f: RWSession => Result[T]): Result[T] = {
    val s = timer("getRWSession")(sessionProvider.getReadWriteSession(db))
    timer("readWrite") {
      try {
        s.withTransaction {
          f(s) match {
            case Good(res) =>
              //stats
              //println(s"readwrite, result: $res")
              Good(res)
            case Bad(err)  =>
              s.rollback()
              Bad(err)
          }
        }
      } finally s.close()
    }
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
}
