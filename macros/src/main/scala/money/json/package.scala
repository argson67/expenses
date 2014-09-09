package money

import scala.language.implicitConversions

import org.json4s.JsonAST._
import scalaz.NonEmptyList

import money.errors._

package object json {
  def fromJson[T](jv: JValue)(implicit ev: JsonFormat[T]) =
    ev.read(jv)

  implicit class JsonOps[T](x: T)(implicit ev: JsonFormat[T]) {
    def toJson = ev.write(x)
  }

  implicit class JsonPathString(str: String) {
    def /(next: String) = NonEmptyList(str, next)
  }

  implicit class JsonPathList(lst: NonEmptyList[String]) {
    def /(next: String) = lst :::> List(next)
  }

  implicit class JValueFinder(jv: JValue) {
    def select[T: JsonFormat](name: String): Result[T] = jv match {
      case JObject(fields) =>
        fields find (_._1 == name) map { f =>
          fromJson[T](f._2)
        } getOrElse implicitly[JsonFormat[T]].notFound(name)
      case other =>
        NotAnObjectError(jv, name)
    }

    def select[T: JsonFormat](path: NonEmptyList[String]): Result[T] =
      path.tail match {
        case Nil =>
          select(path.head)
        case t :: ts =>
          jv match {
            case JObject(fields) =>
              fields find (_._1 == path.head) map { f =>
                f._2.select(NonEmptyList.nel(t, ts))
              } getOrElse MissingField(path.head)
            case other =>
              NotAnObjectError(jv, path.head)
          }
      }
  }
}
