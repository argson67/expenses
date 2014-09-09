package money.relations

import scala.slick.driver.PostgresDriver.simple.{ Database => SlickDatabase, _ }
import scala.slick.ast.{ ColumnOption => O }
import money.db.annotations._
import money.db._
import DBSession._
import money.models._

import money.errors._

@relation
case class ExpenseTarget(@column("expense_id", O.NotNull) expenseId: Id[Expense],
                         @column("target_id", O.NotNull) targetId: Id[User]) extends Relation[ExpenseTarget] {

}

object ExpenseTarget extends RelationCompanion[ExpenseTarget]

@repo[ExpenseTarget]("expense_targets")
@foreignKey("expense", "expense_fk", "expenseId", Expenses, "id")
//@foreignKey("target", "target_fk", "targetId", Users, "id")
@index("expense_target_idx", ("expenseId", "targetId"), unique = true)
object ExpenseTargets extends RelationRepo[ExpenseTarget]
