package money

import scala.slick.driver.PostgresDriver.simple.{ Database => SlickDatabase }
import money.db._
import money.models._
import money.relations._

object DB {
  val slickDB = SlickDatabase.forURL("jdbc:postgresql://localhost/money", driver="org.postgresql.Driver")
  implicit val db = new Database(slickDB)

  val tables: Map[String, DBRepo[_]] =
    List(Users, Debts, Expenses, Reports, ExpenseTargets, RecurringExpenses).map(t => t.tableName -> t).toMap
}
