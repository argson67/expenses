package money

import scala.collection.generic.CanBuildFrom
import scalaz._

import scala.language.implicitConversions
import scala.language.higherKinds
import sun.reflect.generics.reflectiveObjects.NotImplementedException

package object errors {
  case class MiscError(msg: String) extends Error

  /*type Result[+T] = \/[Error, T]
  type Good[+T] = \/-[T]
  type Bad = -\/[Error]

  object Bad {
    def apply(e: Error) = -\/(e)
    def apply(msg: String) = -\/(MiscError(msg))
    def unapply(b: Bad): Option[Error] = Some(b.a)
  }

  object Good {
    def apply[T](v: T): Good[T] = \/-(v)
    def unapply[T](g: Good[T]): Option[T] = Some(g.b)
  }*/

  sealed abstract class Result[+A] {
    def get: A
    def map[B](f: A => B): Result[B]
    def flatMap[B](f: A => Result[B]): Result[B]
    def foreach(f: A => Unit): Unit
    def fold[B](fBad: Error => B, fGood: A => B): B
    def getOrElse[U >: A](default: => U): U
  }

  case class Good[+A](get: A) extends Result[A] {
    @inline
    def map[B](f: A => B): Result[B] = Good(f(get))
    @inline
    def flatMap[B](f: A => Result[B]) = f(get)
    @inline
    def foreach(f: A => Unit): Unit = f(get)
    @inline
    def fold[B](fBad: Error => B, fGood: A => B) = fGood(get)
    @inline
    def getOrElse[U >: A](default: => U): U = get
  }

  case class Bad(err: Error) extends Result[Nothing] {
    def get = throw new NotImplementedException
    @inline
    def map[B](f: Nothing => B): Result[B] = this
    @inline
    def flatMap[B](f: Nothing => Result[B]): Result[B] = this
    @inline
    def foreach(f: Nothing => Unit): Unit = ()
    @inline
    def fold[B](fBad: Error => B, fGood: Nothing => B) = fBad(err)
    @inline
    def getOrElse[U >: Nothing](default: => U): U = default
  }

  object Bad {
    def apply(msg: String): Bad = Bad(MiscError(msg))
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
