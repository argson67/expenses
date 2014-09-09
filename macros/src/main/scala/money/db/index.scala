package money.db

// Scala imports
import scala.annotation.StaticAnnotation

case class index(n: String, on: Any, unique: Boolean = false) extends StaticAnnotation