package money.models

import org.joda.time.DateTime
import com.github.tototoshi.slick.PostgresJodaSupport._

import scala.slick.driver.PostgresDriver.simple.{ Database => SlickDatabase, _ }
import scala.slick.ast.{ ColumnOption => O }
import money.db.annotations._
import money.db._
import money.json._
import DBSession._

import money.relations._
import money.errors._
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import money.data.DateRange

@model
case class Expense(@column("id", O.PrimaryKey, O.Nullable, O.AutoInc) id: Option[Id[Expense]],
                   @column("owner_id", O.NotNull) ownerId: Id[User],
                   @column("date") date: DateTime,
                   @column("amount", O.NotNull) amount: Double,
                   @column("description", O.NotNull) description: String,
                   @column("comment", O.NotNull, O.DBType("text")) comment: String,
                   @column("recurring", O.NotNull) recurring: Boolean,
                   @column("committed", O.NotNull) committed: Boolean) extends Model[Expense] {

  def beneficiaryIds(implicit s: RSession): List[Id[User]] = ExpenseTargets.findByExpenseId(id.get) map (_.targetId)

  def beneficiaries(implicit s: RSession): Result[List[User]] = beneficiaryIds.foldLeft(Good(List.empty[User]): Result[List[User]]) { (soFar, e) =>
    soFar flatMap { lst =>
      Users.getById(e) map (_ :: lst)
    }
  }

  def frequencyNum(implicit s: RSession): Int = RecurringExpenses.getByExpenseId(id.get) map (_.frequencyNum) getOrElse 1

  def frequencyUnit(implicit s: RSession): Int = RecurringExpenses.getByExpenseId(id.get) map (_.frequencyUnit) getOrElse 0

  def publicJson(implicit s: RSession): Result[JObject] =
    for (owner <- Users.getById(ownerId);
         bens  <- beneficiaries) yield {
      ("id" -> id) ~
      ("owner" -> owner.publicJson) ~
      ("date" -> date.toJson) ~
      ("amount" -> amount) ~
      ("description" -> description) ~
      ("comment" -> comment) ~
      ("recurring" -> recurring) ~
      ("committed" -> committed) ~
      ("beneficiaries" -> (bens map (_.publicJson))) ~
      ("frequencyNum" -> frequencyNum) ~
      ("frequencyUnit" -> frequencyUnit)
    }

  def addBeneficiaries(bens: List[Id[User]])(implicit session: RWSession) = {
    println(s"Adding beneficiaries to expense id $id: $bens")
    val thisId = option2Result(id) toResult MiscError("Cannot add beneficiaries to non-existing expense")
    bens.foldLeft(thisId) {
      (soFar, ben) =>
        soFar flatMap { id =>
          if (ExpenseTarget.establish(ExpenseTarget(id, ben))) {
            println(s"Established $ben as beneficiary for expense $id")
            Good(id)
          } else {
            Bad(MiscError(s"Error adding user $ben to expense"))
          }
        }
      }
    }

  def addRecurring(frequencyNum: Int, frequencyUnit: Int)(implicit s: RWSession) = {
    val thisId = option2Result(id) toResult MiscError("Cannot make a non-existing expense recurring")
    thisId flatMap { id =>
      if (recurring) {
        if (RecurringExpense.establish(RecurringExpense(id, frequencyNum, frequencyUnit, None))) Good(id)
        else Bad(MiscError(s"Error establishing expense as recurring"))
      } else {
        Good(id)
      }
    }
  }

  def instantiateRecurring()(implicit s: RWSession): Result[Expense] = {
    if (!recurring || committed) {
      Bad(MiscError("Cannot instantiate non-recurring expense"))
    } else {
      val newEx = Expense(None, ownerId, DateTime.now(), amount, description, comment, recurring = false, committed = false)
      for (saved <- Expense.save(newEx);
           _     <- saved.addBeneficiaries(beneficiaryIds)) yield saved
    }
  }
}

object Expense extends ModelCompanion[Expense]

@repo[Expense]("expenses")
@foreignKey("owner", "owner_fk", "ownerId", Users, "id")
object Expenses extends ModelRepo[Expense] {
  def updateBeneficiaries(expense: Expense, beneficiaries: List[Id[User]])(implicit session: RWSession) = {
    expense.id match {
      case Some(id) =>
        deleteBeneficiaries(id)
        beneficiaries map { ben =>
          ExpenseTarget.establish(ExpenseTarget(id, ben))
        } forall identity
      case _ =>
        false
    }
  }

  def updateRecurring(expense: Expense, frequencyNum: Int, frequencyUnit: Int)(implicit session: RWSession) = {
    if (expense.recurring) {
      expense.id match {
        case Some(id) =>
          deleteRecurring(id)
          RecurringExpense.establish(RecurringExpense(id, frequencyNum, frequencyUnit, None))
        case _ =>
          false
      }
    } else {
      true
    }
  }

  def deleteAll(id: Id[Expense])(implicit session: RWSession) = {
    deleteBeneficiaries(id)
    deleteRecurring(id)
    delete(id)
  }

  def getRange(from: DateTime, to: DateTime)(implicit session: RSession) =
    (for (t <- tableQuery if !t.recurring && t.date >= from && t.date <= to) yield t).list

  def deleteBeneficiaries(id: Id[Expense])(implicit session: RWSession) =
    ExpenseTargets.deleteBy(_.expenseId)(id)

  def deleteRecurring(id: Id[Expense])(implicit session: RWSession) =
    RecurringExpenses.deleteBy(_.expenseId)(id)

  def recurring(implicit session: RSession) =
    (for (t <- tableQuery if t.recurring) yield t).list
}
