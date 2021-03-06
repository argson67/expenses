package money.models

import org.joda.time.DateTime
import com.github.tototoshi.slick.PostgresJodaSupport._

import scala.slick.driver.PostgresDriver.simple.{ Database => SlickDatabase, _ }
import scala.slick.ast.{ ColumnOption => O }
import money.db.annotations._
import money.db._
import money.json._
import money.relations.CommittedExpenses
import DBSession._

import money.errors._
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._

@model
case class Report(@column("id", O.PrimaryKey, O.Nullable, O.AutoInc) id: Option[Id[Report]],
                  @column("date", O.NotNull) date: DateTime,
                  @column("description", O.NotNull) description: String) extends Model[Report] {

  // TODO: dedup
  def fromDate(implicit s: RSession): Result[DateTime] =
    id match {
      case Some(id) =>
        Report.fromDateCompiled(id).firstOption match {
          case Some(e) => Good(e.date)
          case None    => Bad(MiscError(s"Report $id is apparently empty"))
        }
      case None =>
        Bad(MiscError(s"Cannot perform fromDate on uncommitted report"))
    }

  def toDate(implicit s: RSession): Result[DateTime] =
    id match {
      case Some(id) =>
        Report.toDateCompiled(id).firstOption match {
          case Some(e) => Good(e.date)
          case None    => Bad(MiscError(s"Report $id is apparently empty"))
        }
      case None =>
        Bad(MiscError(s"Cannot perform toDate on uncommitted report"))
    }

  def expenses(implicit s: RSession): List[Expense] =
    id match {
      case Some(id) =>
        Report.expensesCompiled(id).list
      case None =>
        Nil
    }

  def publicJson(implicit s: RSession): Result[JObject] =
    for (from <- fromDate;
         to   <- toDate)
    yield {
      ("id" -> id) ~
      ("date" -> date.toJson) ~
      ("description" -> description) ~
      ("fromDate" -> from.toJson) ~
      ("toDate" -> to.toJson)
    }
}

object Report extends ModelCompanion[Report] {
  val fromDateCompiled = {
    def query(id: Column[Id[Report]]) =
      (for (ce <- CommittedExpenses.tableQuery if ce.reportId === id;
            e  <- Expenses.tableQuery if e.id === ce.expenseId)
      yield e).sortBy(_.date).take(1)
    Compiled(query _)
  }

  val toDateCompiled = {
    def query(id: Column[Id[Report]]) =
      (for (ce <- CommittedExpenses.tableQuery if ce.reportId === id;
            e  <- Expenses.tableQuery if e.id === ce.expenseId)
      yield e).sortBy(_.date.desc).take(1)
    Compiled(query _)
  }

  val expensesCompiled = {
    def query(id: Column[Id[Report]]) =
      for  (ce <- CommittedExpenses.tableQuery if ce.reportId === id;
           e  <- Expenses.tableQuery if e.id === ce.expenseId) yield e
    Compiled(query _)
  }
}

@repo[Report]("reports")
object Reports extends ModelRepo[Report]
