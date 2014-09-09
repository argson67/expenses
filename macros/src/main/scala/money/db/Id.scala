package money.db

// Scala imports
import scala.language.implicitConversions

import org.json4s.{ JValue, JInt }

// Slick imports
import scala.slick.driver.PostgresDriver.simple._

import money.errors._

/*
 * Type-safe Id
 * Value class to avoid boxing overhead
 */

case class Id[M <: Model[M]](value: Int) extends AnyVal

object Id {
  implicit def idMapper[M <: Model[M]] = MappedColumnType.base[Id[M], Int](_.value, Id[M])

  implicit def idSerializer[M <: Model[M]](id: Id[M]): JValue = JInt(id.value)

  implicit def idDeSerializer[M <: Model[M]](ji: JInt): Id[M] = Id[M](ji.num.toInt)

  def fromString[M <: Model[M]](idStr: String): Result[Id[M]] =
    try {
      Good(Id[M](Integer.parseInt(idStr)))
    } catch {
      case e: java.lang.NumberFormatException =>
      Bad(e.getMessage)
    }
}
