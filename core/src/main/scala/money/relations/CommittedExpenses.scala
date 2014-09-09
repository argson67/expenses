package money.relations

import scala.slick.driver.PostgresDriver.simple.{ Database => SlickDatabase, _ }
import scala.slick.ast.{ ColumnOption => O }
import money.db.annotations._
import money.db._
import DBSession._
import money.models._

import org.joda.time.DateTime
import com.github.tototoshi.slick.PostgresJodaSupport._

import money.errors._

@relation
case class CommittedExpense(@column("expense_id", O.NotNull) expenseId: Id[Expense],
                            @column("report_id", O.NotNull) reportId: Id[Report]) extends Relation[CommittedExpense] {
}

object CommittedExpense extends RelationCompanion[CommittedExpense]

@repo[CommittedExpense]("committed_expenses")
@foreignKey("expense", "expense_fk", "expenseId", Expenses, "id")
@foreignKey("report", "report_fk", "reportId", Reports, "id")
object CommittedExpenses extends RelationRepo[CommittedExpense]
