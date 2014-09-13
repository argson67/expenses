package money

import akka.actor.Actor
import spray.routing._

import spray.http._
import spray.httpx.Json4sSupport
import org.json4s.{DefaultFormats, Formats}
import money.errors.Error

class MoneyServiceActor extends Actor with MoneyService {
  def actorRefFactory = context
  def receive = runRoute(route)
}

trait Json4sProtocol extends Json4sSupport {
  implicit def json4sFormats: Formats = DefaultFormats
}

// this trait defines our service behavior independently from the service actor
trait MoneyService extends HttpService with MoneyDirectives with ApiRoutes with Json4sProtocol {
  private def time(log: (HttpRequest, HttpResponse, Long) => Unit): Directive0 =
    mapRequestContext { ctx =>
      val timeStamp = System.currentTimeMillis
      ctx.withHttpResponseMapped { response =>
        log(ctx.request, response, System.currentTimeMillis - timeStamp)
        response
      }
    }

  private def logger(request: HttpRequest, response: HttpResponse, t: Long): Unit = {
    println(s"Request: $request, time: $t ms")
  }

  private val exceptionHandler = handleExceptions {
    ExceptionHandler {
      case e: Error =>
        complete(e)
    }
  }

  private val moneyRoutes =
    pathPrefix("api") {
      apiRoute
    } ~
    path("") {
      getFromResource("app/index.html")
    } ~
    pathPrefix("") {
      getFromResourceDirectory("app")
    }

  val route = time(logger) {
    exceptionHandler {
      moneyRoutes
    }
  }
}
