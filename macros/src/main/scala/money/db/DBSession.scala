// http://eng.42go.com/scala-slick-database-drivers-type-safing-session-concurrency/

package money.db

// Java imports
import java.sql.{PreparedStatement, Connection}

// Scala imports
import scala.collection.mutable
import scala.language.implicitConversions

// Slick imports
import scala.slick.driver.PostgresDriver
import PostgresDriver.simple.Session
import PostgresDriver.backend.SessionDef
import scala.slick.jdbc.{ResultSetHoldability, ResultSetConcurrency, ResultSetType}

object DBSession {
  abstract class SessionWrapper(_session: => Session) extends SessionDef {
    lazy val session = _session

    def conn: Connection = session.conn
    def database = session.database.asInstanceOf[PostgresDriver.backend.Database] // hackity-hackity
    def metaData = session.metaData
    def capabilities = session.capabilities.asInstanceOf[PostgresDriver.backend.DatabaseCapabilities] // hack-hack-hack
    override def resultSetType = session.resultSetType
    override def resultSetConcurrency = session.resultSetConcurrency
    override def resultSetHoldability = session.resultSetHoldability
    def close() { session.close() }
    def rollback() { session.rollback() }
    def withTransaction[T](f: => T): T = session.withTransaction(f)

    private val statementCache = new mutable.HashMap[String, PreparedStatement]
    def getPreparedStatement(statement: String): PreparedStatement =
      statementCache.getOrElseUpdate(statement, this.conn.prepareStatement(statement))

    override def forParameters(rsType: ResultSetType = resultSetType, rsConcurrency: ResultSetConcurrency = resultSetConcurrency,
                               rsHoldability: ResultSetHoldability = resultSetHoldability) = throw new UnsupportedOperationException
  }

  abstract class RSession(roSession: => Session) extends SessionWrapper(roSession) {

  }
  class ROSession(roSession: => Session) extends RSession(roSession)
  class RWSession(rwSession: Session) extends RSession(rwSession)

  implicit def roToSession(roSession: ROSession): Session = roSession.session
  implicit def rwToSession(rwSession: RWSession): Session = rwSession.session
}
