package money.errors

case class AuthError(msg: String) extends WebError {
  val errorCode = 403
}
