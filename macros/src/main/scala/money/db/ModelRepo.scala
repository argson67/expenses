package money.db

import money.errors._
import money.db.DBSession.{RWSession, RSession}

import scala.slick.driver.PostgresDriver.simple._
import scala.slick.ast.BaseTypedType
import java.sql.SQLException
import scala.Some

trait ModelRepo[M <: Model[M]] extends DBRepo[M] {
  type R <: ModelTable[M]

  def get(id: Id[M])(implicit s: RSession): Result[M] = {
    val f = getBy(_.id)
    f(id)
  }

  /*def page(page: Int = 0, size: Int = 20)(implicit session: RSession): Seq[M] = {
    val q = for {
      t <- tableQuery
    } yield t
    q.sortBy(_.id desc).drop(page * size).take(size).list
  }*/

  def save(model: M)(implicit s: RWSession): Result[M] = try {
    Good(model.id match {
      case Some(id) =>
        val f = update
        f(model)
      case None =>
        model.withId(insert(model))
    })
  } catch {
    case t: SQLException =>
      Bad(t.getMessage)
  }

  def delete(id: Id[M])(implicit s: RWSession): Boolean =
    (for (t <- tableQuery if t.id === id) yield t).delete == 1

  private def insert(model: M)(implicit s: RWSession): Id[M] = {
    /*
     * Even q2ii(tableQuery) still doesn't help IDEA find the ``returning'' method.
     * And this IS the right implicit (compile with -Xprint:typer to confirm).
     * Annotating the method type explicitly to localize the ``error''.
     */

    tableQuery.returning(tableQuery map (_.id)).insert(model)
  }

  private def update(implicit s: RWSession): M => M = {
    def query(id: Column[Id[M]]) = for (t <- tableQuery if t.id === id) yield t
    val compiled = Compiled(query _)

    (model: M) => {
      val count = compiled(model.id.get).update(model)
      assert(count == 1)
      model
    }
  }
}
