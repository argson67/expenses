package money.db

// Imports from scala
import money.db.DBSession.{RSession, RWSession}
import scala.language.implicitConversions

// Imports from local
import money.errors._

/*
 * All model templates have to explicitly extend Model / ModelCompanion/ DBRepo
 * This isn't because we can't accomplish this with the macro (we can),
 * but if we do it explicitly, we prevent the IDE from complaining about
 * missing methods / fields, etc. and overall gain a better development experience
 * (esp. in the long-term). So this seems like a reasonable tradeoff.
 */

abstract class Model[M <: Model[M]] extends SqlTable[M] {
  def id: Option[Id[M]]

  def withId(id: Id[M]): M
}

trait ModelCompanion[M <: Model[M]] extends SqlTableCompanion[M] {
  def repo: ModelRepo[M]

  def find(idStr: String)(implicit s: RSession): Result[M] = Id.fromString[M](idStr) flatMap find
  def find(id: Id[M])(implicit s: RSession): Result[M] = repo.get(id)

  def save(model: M)(implicit s: RWSession): Result[M] = repo.save(model)

  def delete(id: Id[M])(implicit s: RWSession): Boolean = repo.delete(id)
}
