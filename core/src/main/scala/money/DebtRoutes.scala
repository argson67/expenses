package money

import money.db.Id
import money.models._
import money.relations._
import org.joda.time.DateTime
import money.errors._

import money.json._
import money.json.JsonMacros._
import money.json.JsonFormat._
import org.json4s.JsonAST.{JArray, JValue}

trait DebtRoutes {
  self: MoneyService =>

  private def doPay(user: User, id: Id[Debt]): Result[Id[Debt]] = {
    DB.db.readWrite { implicit s =>
      Debts.getById(id) flatMap { debt: Debt =>
        if (debt.paid) Bad(MiscError("This debt has already been marked as repaid."))
        else if (user.id.get != debt.debtorId) Bad(MiscError("Only the debtor can mark the debt as repaid"))
        else Debt.save(debt.pay) map (_.id.get)
      }
    }
  }

  private def doConfirm(user: User, id: Id[Debt]): Result[Id[Debt]] = {
    DB.db.readWrite { implicit s =>
      Debts.getById(id) flatMap { debt: Debt =>
        if (debt.confirmed) Bad(MiscError("This debt has already been marked as confirmed."))
        else if (!debt.paid) Bad(MiscError("Cannot confirm a debt that hasn't been marked as paid."))
        else if (user.id.get != debt.creditorId) Bad(MiscError("Only the creditor can mark the debt as confirmed"))
        else Debt.save(debt.confirm) map (_.id.get)
      }
    }
  }

  val getDebtsByReport = path("report" / IntNumber) { id =>
    get {
      DB.db.readOnly { implicit s =>
        handleError(Debts.findByReportId(Id[Report](id)).map(_.publicJson).accumulate) { res =>
          complete(JArray(res))
        }
      }
    }
  }

  val getDebtsByDebtor = path("debtor" / IntNumber) { id =>
    get {
      DB.db.readOnly { implicit s =>
        handleError(Debts.byDebtor(Id[User](id)).map(_.publicJson).accumulate) { res =>
          complete(JArray(res))
        }
      }
    }
  }

  val getDebtsByCreditor = path("creditor" / IntNumber) { id =>
    get {
      DB.db.readOnly { implicit s =>
        handleError(Debts.byCreditor(Id[User](id)).map(_.publicJson).accumulate) { res =>
          complete(JArray(res))
        }
      }
    }
  }

  val pay = path("pay" / IntNumber) { id =>
    post {
      withAuth { u =>
        handleError(doPay(u, Id[Debt](id))) { res =>
          complete(res)
        }
      }
    }
  }

  val confirm = path("confirm" / IntNumber) { id =>
    post {
      withAuth { u =>
        handleError(doConfirm(u, Id[Debt](id))) { res =>
          complete(res)
        }
      }
    }
  }

  val debtRoute = pathPrefix("debts") { getDebtsByReport ~ getDebtsByDebtor ~ getDebtsByCreditor ~ pay ~ confirm }
}
