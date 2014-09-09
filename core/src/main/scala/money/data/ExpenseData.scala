package money.data

import org.joda.time.DateTime

import money.models._
import money.db._

case class ExpenseData(id: Option[Id[Expense]],
                       ownerId: Id[User],
                       amount: Double,
                       description: String,
                       comment: String,
                       recurring: Boolean,
                       frequencyNum: Int,
                       frequencyUnit: Int,
                       beneficiaries: List[Id[User]])
