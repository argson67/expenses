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
import spray.http.{HttpResponse, HttpRequest}

trait ExpenseRoutes {
  self: MoneyService =>

  private def doCreateExpense(data: ExpenseData) = {
    DB.db.readWrite { implicit s =>
      val date = data.date.getOrElse(DateTime.now())

      println(s"Create expense request: $data")

      val ex = Expense(None, data.ownerId, date, data.amount, data.description, data.comment, data.recurring, committed = false)
      val newEx = Expense.save(ex)

      for (e <- newEx;
           _ <- e.addBeneficiaries(data.beneficiaries);
           _ <- e.addRecurring(data.frequencyNum, data.frequencyUnit)) yield e.id
    }
  }

  private def doEditExpense(id: Id[Expense])(data: ExpenseData): Result[Id[Expense]] = {
    DB.db.readWrite { implicit s =>
      timer("editExpense:readWrite") {
        val oldExRes: Result[Expense] = timer("expenses:getbyid")(Expenses.getById(id))
        oldExRes flatMap { oldEx: Expense =>
          if (oldEx.committed) {
            Bad(MiscError("You cannot edit a committed expense"))
          } else {
            val date = data.date.getOrElse(oldEx.date)

            println(s"Edit expense request: $data")

            val ex = Expense(Some(id), data.ownerId, date, data.amount, data.description, data.comment, data.recurring, oldEx.committed)
            val newEx = timer("Saving expenses")(Expense.save(ex))
            newEx flatMap { e =>
              if (!timer("updateBens")(Expenses.updateBeneficiaries(e, data.beneficiaries))) Bad(MiscError("Error updating expense beneficiaries"))
              else if (!timer("updateRec")(Expenses.updateRecurring(e, data.frequencyNum, data.frequencyUnit))) Bad(MiscError("Error updating recurring expense"))
              else Good(e.id.get)
            }
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
      for (from <- timer("parseFrom")(parseDate(fromDate));
           to   <- timer("parseTo")(parseDate(toDate));
           tmp1 =  timer("getInRange")(Expenses.getInRange(from, to));
           tmp  =  timer("publicJson")(tmp1.map(_.publicJson));
           lst  <- timer("accumulate")(tmp.accumulate)
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

  private def expenseLogger(name: String)(req: HttpRequest, res: HttpResponse, delta: Long): Unit = {
    println(s"Time elapsed in $name: $delta")
  }

  val editExpense = path(IntNumber) { id =>
    val expenseId = Id[Expense](id)
    post {
      time(expenseLogger("post")) {
        withAuth { u =>
          time(expenseLogger("withAuth")) {
            val ej = time(expenseLogger("ej")) & extractJson[ExpenseData]
            ej { data =>
              (time(expenseLogger("handleError")) & handleError(data flatMap { d =>
                  timer("first part of handleError") {
                    if (expenseId != d.id.get) Bad(MiscError("Inconsistent expense ids in edit request"))
                    else if (d.ownerId != u.id.get && !u.admin) Bad(AuthError("You are not authorized to edit this expense"))
                    else Good(d)
                  }
                } flatMap doEditExpense(expenseId))) { res =>
                  complete(res)
                }
            }
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

  val getExpensesByReport = path("report" / IntNumber) { id =>
    get {
      DB.db.readOnly { implicit s =>
        handleError(Expenses.getByReport(Id[Report](id)).map(_.publicJson).accumulate) { res =>
          complete(JArray(res))
        }
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

  val expenseRoute = getRecurringExpenses ~ getExpenses ~ pathPrefix("expense") {
    createExpense ~ editExpense ~ deleteExpense ~ getExpense ~ getExpensesByReport
  }
}
