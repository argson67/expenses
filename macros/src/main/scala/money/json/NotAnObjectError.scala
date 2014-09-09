package money.json

import money.errors._
import org.json4s.JsonAST.JValue

case class NotAnObjectError(jv: JValue, field: String) extends Error {
  val msg = s"Cannot select field '$field' from $jv: not an object"
}
