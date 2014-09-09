package money.db.annotations

// Scala imports
import scala.annotation.StaticAnnotation

// Slick imports
import scala.slick.ast.ColumnOption

case class column(n: String, options: ColumnOption[Any]*) extends StaticAnnotation
