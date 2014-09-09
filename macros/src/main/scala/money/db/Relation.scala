package money.db

// Imports from scala
import money.db.DBSession.{RSession, RWSession}

abstract class Relation[M <: Relation[M]] extends SqlTable[M]

trait RelationCompanion[M <: Relation[M]] extends SqlTableCompanion[M] {
  def repo: RelationRepo[M]

  def holds(rel: M)(implicit s: RSession): Boolean = repo.holds(rel)

  def establish(rel: M)(implicit s: RWSession): Boolean = repo.establish(rel)
}
