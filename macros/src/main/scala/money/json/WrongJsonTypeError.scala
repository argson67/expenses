package money.json

import money.errors._
import org.json4s.JsonAST.JValue

import scala.reflect.runtime.{ universe => ru }

case class WrongJsonTypeError(typeName: String, jv: JValue) extends Error {
  val msg = s"Cannot read $typeName from $jv"
}