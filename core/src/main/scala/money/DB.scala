package money

import scala.slick.driver.PostgresDriver.simple.{ Database => SlickDatabase }
import com.mchange.v2.c3p0.ComboPooledDataSource

import money.db._
import money.models._
import money.relations._

object DB {
  //val slickDB = SlickDatabase.forURL("jdbc:postgresql://localhost/money", driver="org.postgresql.Driver")
  val cpds = new ComboPooledDataSource

  cpds.setDriverClass("org.postgresql.Driver") //loads the jdbc driver
  cpds.setJdbcUrl("jdbc:postgresql://localhost/money") // cpds.setUser("dbuser"); cpds.setPassword("dbpassword");
  cpds.setMinPoolSize(5)
  cpds.setAcquireIncrement(5)
  cpds.setMaxPoolSize(20)
  cpds.setMaxStatements(200)
  cpds.setMaxStatementsPerConnection(20)

  val slickDB = SlickDatabase.forDataSource(cpds)
  implicit val db = new Database(slickDB, cpds)

  val tables: Map[String, DBRepo[_]] =
    List(Users, Debts, Expenses, Reports, ExpenseTargets, RecurringExpenses, CommittedExpenses).map(t => t.tableName -> t).toMap
}
