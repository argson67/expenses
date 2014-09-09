package money

import scala.collection.generic.CanBuildFrom
import scalaz._

import scala.language.implicitConversions
import scala.language.higherKinds

package object errors {
  type Result[+T] = \/[Error, T]
  type Good[+T] = \/-[T]
  type Bad = -\/[Error]

  case class MiscError(msg: String) extends Error

  object Bad {
    def apply(e: Error) = -\/(e)
    def apply(msg: String) = -\/(MiscError(msg))
    def unapply(b: Bad): Option[Error] = Some(b.a)
  }

  object Good {
    def apply[T](v: T): Good[T] = \/-(v)
    def unapply[T](g: Good[T]): Option[T] = Some(g.b)
  }

  implicit def error2Result[T](err: Error): Result[T] = Bad(err)

  /*implicit def option2Result[T](opt: Option[T]): Result[T] = opt match {
    case Some(value) => Good(value)
    case None        => Bad("Generic error")
  }*/

  //implicit def result2Option[T](res: Result[T]): Option[T] = res.toOption

  implicit class option2Result[T](opt: Option[T]) {
    def toResult(err: Error): Result[T] = opt map Good.apply getOrElse Bad(err)
  }

  implicit class accumulateResults[T, F[X] <: Iterable[X]](iter: F[Result[T]]) {
    def accumulate(implicit cbf: CanBuildFrom[F[_], T, F[T]]) = {
      val builder = cbf.apply()
      var err: Option[Error] = None

      iter foreach {
        case Good(x) =>
          if (err.isEmpty) builder += x
        case Bad(e) =>
          err = Some(e)
      }

      err match {
        case Some(e) => Bad(e)
        case None    => Good(builder.result())
      }
    }
  }
}
