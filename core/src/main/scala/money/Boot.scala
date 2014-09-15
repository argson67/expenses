package money

import akka.actor.{Props, ActorSystem}
import spray.servlet.WebBoot
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

// this class is instantiated by the servlet initializer
// it needs to have a default constructor and implement
// the spray.servlet.WebBoot trait
class Boot extends WebBoot with Cron {
  // we need an ActorSystem to host our application in
  val system = ActorSystem("money")

  // the service actor replies to incoming HttpRequests
  val serviceActor = system.actorOf(Props[MoneyServiceActor])

  import ExecutionContext.Implicits.global
  system.scheduler.schedule(0.second, 5.minute)(checkRecurring())
}
