package money

import akka.actor.{ Actor, ActorSystem }
import spray.routing._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import spray.http._
import spray.httpx.Json4sSupport
import org.json4s.{DefaultFormats, Formats}
import money.errors.Error

class MoneyServiceActor extends Actor with MoneyService {
  def actorRefFactory = context
  def receive = runRoute(route)

  private val system = ActorSystem("money")
  import ExecutionContext.Implicits.global
  system.scheduler.schedule(0.second, 1.day)(checkExpiredSessions())
}

trait Json4sProtocol extends Json4sSupport {
  implicit def json4sFormats: Formats = DefaultFormats
}

// this trait defines our service behavior independently from the service actor
trait MoneyService extends HttpService with MoneyDirectives with ApiRoutes with Json4sProtocol {
  protected def time(log: (HttpRequest, HttpResponse, Long) => Unit): Directive0 =
    mapRequestContext { ctx =>
      val timeStamp = System.currentTimeMillis
      ctx.withHttpResponseMapped { response =>
        log(ctx.request, response, System.currentTimeMillis - timeStamp)
        response
      }
    }

  protected def timer[R](name: String)(block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    val ms = (t1 - t0) / 1000000
    println(s"Elapsed time in '$name': $ms ms")
    result
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
