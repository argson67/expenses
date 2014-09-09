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
    (for (ce <- CommittedExpenses.tableQuery;
         e  <- Expenses.tableQuery if e.id === ce.expenseId)
    yield e).sortBy(_.date).take(1).list.headOption match {
      case Some(e) => Good(e.date)
      case None    => Bad(MiscError(s"Report $id is apparently empty"))
    }

  def toDate(implicit s: RSession): Result[DateTime] =
    (for (ce <- CommittedExpenses.tableQuery;
          e  <- Expenses.tableQuery if e.id === ce.expenseId)
    yield e).sortBy(_.date.desc).take(1).list.headOption match {
      case Some(e) => Good(e.date)
      case None    => Bad(MiscError(s"Report $id is apparently empty"))
    }

  def expenses(implicit s: RSession): List[Expense] =
    (for (ce <- CommittedExpenses.tableQuery;
         e  <- Expenses.tableQuery if e.id === ce.expenseId)
    yield e).list

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

object Report extends ModelCompanion[Report]

@repo[Report]("reports")
object Reports extends ModelRepo[Report]
