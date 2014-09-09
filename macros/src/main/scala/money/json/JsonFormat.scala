package money.json

import money.errors._
import money.db.{ Model, Id }
import org.json4s.JsonAST._
import scala.util.Try
import scalaz.Scalaz._
import org.joda.time.DateTime

import scala.reflect.runtime. { universe => ru }

trait JsonFormat[T] {
  def typeName: String

  def notFound(field: String): Result[T] = Bad(MissingField(field))

  def read(jv: JValue): Result[T] = if (_read.isDefinedAt(jv)) {
    _read(jv)
  } else {
    WrongJsonTypeError(typeName, jv)
  }

  protected def _read: PartialFunction[JValue, Result[T]]
  def write(x: T): JValue
}

object JsonFormat {
  import org.json4s.MappingException

  type ==>[-A, +B] = PartialFunction[A, B]

  implicit def legacyFormat[T](implicit ev: org.json4s.JsonFormat[T], ev1: ru.TypeTag[T]): JsonFormat[T] = new JsonFormat[T] {
    val typeName = ru.typeOf[T].toString

    protected val _read: JValue ==> Result[T] = {
      case jv: JValue =>
        Try(ev.read(jv))
          .map(Good.apply)
          .recover { case _: MappingException => Bad(WrongJsonTypeError(typeName, jv)) }
          .get
    }

    def write(x: T) = ev.write(x)
  }

  implicit val stringFormat = new JsonFormat[String] {
    val typeName = "String"

    protected val _read: JValue ==> Result[String] = {
      case JString(str) => Good(str)
      case JInt(i)      => Good(i.toString)
      case JDouble(d)   => Good(d.toString)
      case JDecimal(d)  => Good(d.toString)
      case JBool(b)     => Good(b.toString)
    }

    def write(x: String) = JString(x)
  }

  implicit val intFormat = new JsonFormat[Int] {
    val typeName = "Int"

    protected val _read: JValue ==> Result[Int] = {
      case JInt(i)      => Good(i.toInt)
      case JDouble(d)   => Good(d.toInt)
      case JString(s)   => try {
        Good(Integer.parseInt(s))
      } catch {
        case _: java.lang.NumberFormatException =>
          Bad(s"Malformed integer: $s")
      }
      case JDecimal(d)  => Good(d.toInt)
      case JBool(b)     => Good(if (b) 1 else 0)
    }

    def write(x: Int) = JInt(x)
  }

  implicit val doubleFormat = new JsonFormat[Double] {
    val typeName = "Double"

    protected val _read: JValue ==> Result[Double] = {
      case JInt(i)      => Good(i.toDouble)
      case JDouble(d)   => Good(d)
      case JString(s)   => try {
        Good(java.lang.Double.parseDouble(s))
      } catch {
        case _: java.lang.NumberFormatException =>
          Bad(s"Malformed double: $s")
      }
      case JDecimal(d)  => Good(d.toDouble)
      case JBool(b)     => Good(if (b) 1.0 else 0.0)
    }

    def write(x: Double) = JDouble(x)
  }

  implicit def idFormat[T <: Model[T]](implicit ev: ru.TypeTag[T]) = new JsonFormat[Id[T]] {
    val typeName = s"Id[${ru.typeOf[T].toString}]"

    protected val _read: JValue ==> Result[Id[T]] = {
      case JInt(x) => Good(Id[T](x.toInt))
    }

    def write(x: Id[T]) = JInt(x.value)
  }

  def parseDate(str: String): Result[DateTime] =
    try {
      Good(DateTime.parse(str))
    } catch {
      case e: java.lang.IllegalArgumentException =>
        Bad(JsonParseError(e.getMessage))
      case e: org.joda.time.IllegalFieldValueException =>
        Bad(JsonParseError(e.getMessage))
    }

  implicit val dateFormat = new JsonFormat[DateTime] {
    val typeName = "DateTime"

    protected val _read: JValue ==> Result[DateTime] = {
      case JString(str) => parseDate(str)
    }

    def write(date: DateTime) = JString(date.toString("yyyy-MM-dd"))
  }

  implicit def listFormat[T](implicit ev: JsonFormat[T]): JsonFormat[List[T]] = new JsonFormat[List[T]] {
    val typeName = s"List[${ev.typeName}]"

    override def notFound(field: String) = Good(List.empty[T])

    protected val _read: JValue ==> Result[List[T]] = {
      case JArray(arr) =>
        arr.foldRight(Good(List.empty[T]): Result[List[T]]) {
          (v, soFar) =>
            for (head <- ev.read(v);
                 tail <- soFar)
            yield head :: tail
        }
      case JNull =>
        Good(List.empty[T])
    }

    def write(lst: List[T]) =
      JArray(lst map ev.write)
  }

  implicit def optionFormat[T](implicit ev: JsonFormat[T]): JsonFormat[Option[T]] = new JsonFormat[Option[T]] {
    val typeName = s"Option[${ev.typeName}]"

    override def notFound(field: String) = Good(none[T])

    protected val _read: JValue ==> Result[Option[T]] = {
      case jv: JValue => ev.read(jv) match {
        case Good(res) =>
          Good(Some(res))
        case Bad(WrongJsonTypeError(_, JNull)) =>
          Good(None)
        case err: Bad =>
          err
      }
    }

    def write(opt: Option[T]) = opt match {
      case Some(x) => ev.write(x)
      case None => JNull
    }
  }

  implicit val booleanFormat: JsonFormat[Boolean] = new JsonFormat[Boolean] {
    val typeName = "Boolean"

    override def notFound(field: String) = Good(false)

    protected val _read: JValue ==> Result[Boolean] = {
      case jv: JBool =>
        Good(jv.value)
      case JNull =>
        Good(false)
    }

    def write(b: Boolean) = JBool(b)
  }
}
