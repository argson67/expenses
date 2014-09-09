package money.errors

import org.json4s.JsonAST._

trait Error extends Throwable {
  def msg: String
  def toJson: JValue = JObject("error" -> JString(msg))
}

object Error {
  def apply(msg: String) = MiscError(msg)
  def unapply(err: Error): Option[String] = Some(err.msg)
}
