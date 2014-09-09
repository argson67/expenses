package money.db

// Slick imports
import scala.slick.driver.PostgresDriver.simple._

abstract class ModelTable[M <: Model[M]](tag: Tag, name: String) extends Table[M](tag, name) {
  def id: Column[Id[M]]
}
