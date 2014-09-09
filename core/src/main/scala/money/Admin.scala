package money

import spray.httpx.marshalling.ToResponseMarshaller
import scala.xml.Elem
import spray.http._
import spray.http.HttpResponse

trait Admin {
  self: MoneyService =>

  private val descRoute = path("desc" / Rest) { s =>
    get {
      import DB._

      val descriptions = tables map { case (k, v) => k -> v.ddl.createStatements }

      complete {
        val res = <div>{ descriptions(s) map ( d =>  <p>{d}</p>) }</div>
        res
      }
    }
  } ~ path("desc") {
    get {
      import DB._

      val stmts = (tables.values map (_.ddl) reduce (_ ++ _)).createStatements

      complete {
        val res = <div>{ stmts map ( d =>  <p>{d}</p>) }</div>
        res
      }
    }
  }

  private val createRoute = path("create" / Rest) { r =>
    get {
      complete {
        import DB._
        tables(r).create.toString
      }
    }
  } ~ path("create") {
    complete {
      import DB._
      import scala.slick.driver.PostgresDriver.simple._
      db.readOnly { implicit s =>
        (tables.values map (_.ddl) reduce (_ ++ _)).create.toString
      }
    }
  }

  private val dropRoute = path("drop" / Rest) { r =>
    get {
      complete {
        import DB._
        tables(r).drop.toString
      }
    }
  } ~ path("drop") {
    complete {
      import DB._
      import scala.slick.driver.PostgresDriver.simple._
      db.readOnly { implicit s =>
        (tables.values map (_.ddl) reduce (_ ++ _)).drop.toString
      }
    }
  }

  val adminRoute = descRoute ~ createRoute ~ dropRoute
}