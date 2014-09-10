package money

import scala.collection.mutable

import money.db.Id
import money.models._
import money.relations._
import org.joda.time.DateTime
import money.errors._

import money.json._
import money.json.JsonMacros._
import money.json.JsonFormat._
import org.json4s.JsonAST.{JArray, JValue}
import money.db.DBSession.RWSession

trait ReportRoutes {
  self: MoneyService =>

  // TODO: Report description

  private def generateDebts(expenses: List[Expense], reportId: Id[Report])(implicit s: RWSession): Result[List[Debt]] = {
    // (creditor, debtor) => amount
    val sums = mutable.Map.empty[(Id[User], Id[User]), Double]
    expenses foreach { exp =>
      assert(exp.committed)

      val owner = exp.ownerId
      val bens = exp.beneficiaryIds
      val each = exp.amount / bens.length

      bens foreach { ben =>
        sums.get((owner, ben)) match {
          case Some(amt) => sums((owner, ben)) = amt + each
          case None      => sums((owner, ben)) = each
        }

        sums.get((ben, owner)) match {
          case Some(amt) => sums((ben, owner)) = amt - each
          case None      => sums((ben, owner)) = - each
        }
      }
    }

    sums.filter(_._2 > 0).foldLeft(Good(Nil): Result[List[Debt]]) { (soFar, e) =>
      for (lst                           <- soFar;
           ((creditorId, debtorId), amt) =  e;
           // TODO: How to properly round this?
           roundedAmt                    =  BigDecimal(amt).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble;
           debt                          <- Debts.save(Debt(None, debtorId, creditorId, reportId, roundedAmt, paid = false, confirmed = false)))
      yield debt :: lst
    }
  }

  private def doGenerate(user: User, fromDate: String, toDate: String): Result[Id[Report]] = {
    DB.db.readWrite { implicit s =>
      val expenses = for (from <- parseDate(fromDate);
                          to   <- parseDate(toDate)) yield Expenses.getUncommittedInRange(from, to)
      expenses flatMap { exp =>
        if (exp.isEmpty) Bad(MiscError("No uncommitted expenses in given range; resulting report is empty"))
        else {
          for (report   <- Reports.save(Report(None, DateTime.now(), ""));
               reportId =  report.id.get;
               cexp     <- Expenses.commit(reportId, exp);
               debts    <- generateDebts(cexp, reportId)) yield reportId
        }
      }
    }
  }

  val getReport = path(IntNumber) { id =>
    get {
      DB.db.readOnly { implicit s =>
        handleError(Reports.getById(Id[Report](id)) flatMap (_.publicJson)) { res =>
          complete(res)
        }
      }
    }
  }

  val generate = path("generate" / Segment / Segment) { (fromDate: String, toDate: String) =>
    post {
      withAuth { u =>
        handleError(doGenerate(u, fromDate, toDate)) { res =>
          complete(res)
        }
      }
    }
  }

  val getReports = path("reports") {
    def inner = DB.db.readOnly { implicit s =>
      handleError(Reports.all.map(_.publicJson).accumulate) { res =>
        complete(JArray(res))
      }
    }
    (get | post).apply(ctx => inner(ctx))
  }

  val reportRoute = getReports ~ pathPrefix("report") { getReport ~ generate }
}
