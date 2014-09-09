package money.errors

abstract class WebError extends Error {
  def errorCode: Int
}
