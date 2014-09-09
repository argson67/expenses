package money.db

import money.db.DBSession.{ RWSession, RSession }

import scala.slick.driver.PostgresDriver.simple._
import java.sql.SQLException

trait RelationRepo[M <: Relation[M]] extends DBRepo[M] {
  type R <: Table[M]

  def establish(rel: M)(implicit s: RWSession): Boolean = try {
    insert(rel) == 1
  } catch {
    case t: SQLException =>
      false
  }

  def holds(rel: M)(implicit s: RSession): Boolean

  private def insert(rel: M)(implicit s: RWSession) =
    tableQuery.insert(rel)
}
