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
case class RecurringExpense(@column("expense_id", O.NotNull) expenseId: Id[Expense],
                            @column("frequency_num", O.NotNull) frequencyNum: Int,
                            @column("frequency_unit", O.NotNull) frequencyUnit: Int,
                            @column("last_run", O.Nullable) lastRun: Option[DateTime]) extends Relation[RecurringExpense] {
  def update(date: DateTime) = RecurringExpense(expenseId, frequencyNum, frequencyUnit, Some(date))
}

object RecurringExpense extends RelationCompanion[RecurringExpense]

@repo[RecurringExpense]("recurring_expenses")
@foreignKey("expense", "expense_fk", "expenseId", Expenses, "id")
object RecurringExpenses extends RelationRepo[RecurringExpense]
