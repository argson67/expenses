package money.db

import money.errors._

case class DBError(msg: String) extends Error
