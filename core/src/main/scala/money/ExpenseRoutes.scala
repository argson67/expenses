package money

import money.db.Id
import money.data.ExpenseData
import money.models._
import money.relations._
import org.joda.time.DateTime
import money.errors._

import money.json._
import money.json.JsonMacros._
import money.json.JsonFormat._
import org.json4s.JsonAST.{JArray, JValue}

trait ExpenseRoutes {
  self: MoneyService =>

  private def doCreateExpense(data: ExpenseData) = {
    DB.db.readWrite { implicit s =>
      val date = DateTime.now() //if (data.recurring) null else DateTime.now()
      val ex = Expense(None, data.ownerId, date, data.amount, data.description, data.comment, data.recurring, committed = false)
      val newEx = Expense.save(ex)

      for (e <- newEx;
           _ <- e.addBeneficiaries(data.beneficiaries);
           _ <- e.addRecurring(data.frequencyNum, data.frequencyUnit)) yield e.id
    }
  }

  private def doEditExpense(id: Id[Expense])(data: ExpenseData): Result[Id[Expense]] = {
    DB.db.readWrite { implicit s =>
      val oldExRes: Result[Expense] = Expenses.getById(id)
      oldExRes flatMap { oldEx: Expense =>
        if (oldEx.committed) {
          Bad(MiscError("You cannot edit a committed expense"))
        } else {
          val ex = Expense(Some(id), data.ownerId, oldEx.date, data.amount, data.description, data.comment, data.recurring, oldEx.committed)
          val newEx = Expense.save(ex)
          newEx flatMap { e =>
            if (!Expenses.updateBeneficiaries(e, data.beneficiaries)) Bad(MiscError("Error updating expense beneficiaries"))
            else if (!Expenses.updateRecurring(e, data.frequencyNum, data.frequencyUnit)) Bad(MiscError("Error updating recurring expense"))
            else Good(e.id.get)
          }
        }
      }
    }
  }

  private def doDeleteExpense(id: Id[Expense], u: User) = {
    DB.db.readWrite { implicit s =>
      val ex: Result[Expense] = Expenses.getById(id)
      ex flatMap { e =>
        if (e.ownerId != u.id.get && !u.admin) Bad(AuthError("You are not authorized to delete this expense"))
        else if (e.committed) Bad(AuthError("You cannot delete a committed expense"))
        else if (Expense.delete(id)) {
          Expenses.deleteBeneficiaries(id)
          Expenses.deleteRecurring(id)
          println(s"Successfully deleted expense $id")
          Good(id)
        } else Bad("Error deleting expense")
      }
    }
  }

  private def doGetExpense(id: Id[Expense]): Result[JValue] = {
    DB.db.readOnly { implicit s =>
      Expenses.getById(id) flatMap (_.publicJson)
    }
  }

  private def doGetExpenses(fromDate: String, toDate: String): Result[JValue] = {
    DB.db.readOnly { implicit s =>
      for (from <- parseDate(fromDate);
           to   <- parseDate(toDate);
           lst  <- Expenses.getRange(from, to).map(_.publicJson).accumulate
      ) yield {
        JArray(lst)
      }
    }
  }

  val createExpense = pathEnd {
    put {
      withAuth { u =>
        val ej = extractJson[ExpenseData]
        ej { data =>
          handleError(data flatMap doCreateExpense) { res =>
            complete(res)
          }
        }
      }
    }
  }

  val editExpense = path(IntNumber) { id =>
    val expenseId = Id[Expense](id)
    post {
      withAuth { u =>
        val ej = extractJson[ExpenseData]
        ej { data =>
          handleError(data flatMap { d =>
            if (expenseId != d.id.get) Bad(MiscError("Inconsistent expense ids in edit request"))
            else if (d.ownerId != u.id.get && !u.admin) Bad(AuthError("You are not authorized to edit this expense"))
            else Good(d)
          } flatMap doEditExpense(expenseId)) { res =>
            complete(res)
          }
        }
      }
    }
  }

  val deleteExpense = path(IntNumber) { id =>
    delete {
      withAuth { u =>
        handleError(doDeleteExpense(Id[Expense](id), u)) { res =>
          complete(res)
        }
      }
    }
  }

  val getExpense = path(IntNumber) { id =>
    get {
      withAuth { u =>
        handleError(doGetExpense(Id[Expense](id))) { res =>
          complete(res)
        }
      }
    }
  }

  val getExpenses = path("expenses" / Segment / Segment) { (fromDate: String, toDate: String) =>
    (get | post) {
      handleError(doGetExpenses(fromDate, toDate)) { res =>
        complete(res)
      }
    }
  }

  val getRecurringExpenses = path("recurringExpenses") {
    def inner = DB.db.readOnly { implicit s =>
      handleError(Expenses.recurring.map(_.publicJson).accumulate) { res =>
        complete(JArray(res))
      }
    }
    // Need to wrap inner inside a lambda to force recalculation on each request
    (get | post).apply(ctx => inner(ctx))
  }

  val expenseRoute = getRecurringExpenses ~ getExpenses ~ pathPrefix("expense") { createExpense ~ editExpense ~ deleteExpense ~ getExpense }
}
