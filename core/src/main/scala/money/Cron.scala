package money

import scala.concurrent.duration._

import money.relations.{RecurringExpense, RecurringExpenses}
import money.models.Expenses
import org.joda.time.DateTime

trait Cron {
  private def applyRecurring(rec: RecurringExpense): Unit = {
    DB.db.readWrite { implicit s =>
      for (expense <- Expenses.get(rec.expenseId);
           newEx   <- expense.instantiateRecurring())
      yield {
        RecurringExpenses.deleteByExpenseId(rec.expenseId)
        RecurringExpense.establish(rec.update(newEx.date))
      }
    }
  }

  def checkRecurring(): Unit = {
    //println("CHECKING RECURRING SHIT")

    val recs = DB.db.readOnly { implicit s =>
      RecurringExpenses.all
    }

    // TODO: Use ADT for frequency unit instead
    def offset(lastRun: DateTime)(frequencyUnit: Int) = Map (
      0 -> 1.day,
      1 -> 7.day,
      2 -> lastRun.dayOfMonth().getMaximumValue.day
    )(frequencyUnit).toMillis

    recs foreach { rec =>
      rec.lastRun match {
        case None =>
          applyRecurring(rec)
        case Some(lastRun) =>
          val target = lastRun.withDurationAdded(offset(lastRun)(rec.frequencyUnit), rec.frequencyNum)
          //println(s"Now: ${DateTime.now()}, target: $target")
          if (target.isBeforeNow) {
            applyRecurring(rec)
          }
      }
    }
  }
}
