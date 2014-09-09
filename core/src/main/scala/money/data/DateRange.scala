package money.data

import org.joda.time.DateTime

case class DateRange(from: DateTime, to: DateTime) {
  assert(to.isAfter(from))
}
