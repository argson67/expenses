package money

import scala.language.implicitConversions

import spray.routing.{Directive1, Directive0, Route, Directives}
import org.json4s.JsonAST._

import money.json._
import money.errors._
import money.db.{ Id, Model }
import spray.httpx.marshalling.{ToResponseMarshallingContext, ToResponseMarshaller, ToResponseMarshallable}
import scala.xml.Elem
import spray.http._
import spray.http.HttpResponse

trait MoneyDirectives {
  self: MoneyService =>

  implicit def fromXml: ToResponseMarshaller[Elem] = ToResponseMarshaller { (value, ctx) =>
    ctx.marshalTo(HttpResponse(StatusCode.int2StatusCode(200), HttpEntity(ContentType(MediaTypes.`text/html`), value.toString)))
  }

  implicit def fromResult[T](implicit m: ToResponseMarshaller[T], j: ToResponseMarshaller[JValue]): ToResponseMarshaller[Result[T]] =
    ToResponseMarshaller { (res: Result[T], ctx: ToResponseMarshallingContext) =>
      res.fold(err => j(err.toJson, ctx), r => m(r, ctx))
    }

  implicit def fromError(implicit j: ToResponseMarshaller[JValue]): ToResponseMarshaller[Error] =
    ToResponseMarshaller { (err: Error, ctx: ToResponseMarshallingContext) =>
      val errorCode = err match {
        case we: WebError => we.errorCode
        case _            => 500
      }

      ctx.marshalTo(HttpResponse(StatusCode.int2StatusCode(errorCode), HttpEntity(ContentType(MediaTypes.`text/html`), err.msg)))
    }

  implicit def fromId[T <: Model[T]](implicit j: ToResponseMarshaller[JValue]): ToResponseMarshaller[Id[T]] =
    ToResponseMarshaller { (id: Id[T], ctx: ToResponseMarshallingContext) =>
      j(JInt(id.value), ctx)
    }

  implicit def result2directive[T](res: => Result[T]): Directive1[T] = res match {
    case Good(x)  => provide(x)
    case Bad(err) => throw err
  }

  implicit def result2route[T](res: => Result[T])(implicit ev: T => ToResponseMarshallable): Route = res match {
    case Good(x)  => complete(x)
    case Bad(err) => throw err
  }

  /*
  implicit def option2directive[T](res: => Option[T]): Directive1[T] = res match {
    case Some(x) => provide(x)
    case None    => throw MiscError("")
  }*/

  def handleError[T](res: Result[T]): Directive1[T] = res

  def extractJson[T: JsonFormat]: Directive1[Result[T]] =
    entity(as[JValue]) map fromJson[T]
}
