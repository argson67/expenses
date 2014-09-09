package money.json

import money.errors._

case class MissingField(field: String) extends Error {
  val msg = s"Missing field: '$field'"
}