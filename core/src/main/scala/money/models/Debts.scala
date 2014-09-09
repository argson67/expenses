package money.models

import org.joda.time.DateTime
import com.github.tototoshi.slick.PostgresJodaSupport._

import scala.slick.driver.PostgresDriver.simple.{ Database => SlickDatabase, _ }
import scala.slick.ast.{ ColumnOption => O }
import money.db.annotations._
import money.db._
import DBSession._

import money.errors._

@model
case class Debt(@column("id", O.PrimaryKey, O.Nullable, O.AutoInc) id: Option[Id[Debt]],
                @column("debtor_id", O.NotNull) debtorId: Id[User],
                @column("creditor_id", O.NotNull) creditorId: Id[User],
                @column("report_id", O.NotNull) reportId: Id[Report],
                @column("amount", O.NotNull) amount: Double,
                @column("payed", O.NotNull) payed: Boolean,
                @column("confirmed", O.NotNull) confirmed: Boolean) extends Model[Debt] {

}

object Debt extends ModelCompanion[Debt]

@repo[Debt]("debts")
@foreignKey("debtor", "debtor_fk", "debtorId", Users, "id")
@foreignKey("creditor", "creditor_fk", "creditorId", Users, "id")
@foreignKey("report", "report_fk", "reportId", Reports, "id")
object Debts extends ModelRepo[Debt]
