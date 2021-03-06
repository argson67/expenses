package money

import money.models._
import org.json4s.JsonAST.{JValue, JArray}

import money.db.Id
import money.errors._

trait UserRoutes {
  self: MoneyService =>

  private def doGetUser(id: Id[User]): Result[JValue] = {
    DB.db.readOnly { implicit s =>
      Users.getById(id) map (_.publicJson)
    }
  }

  val activeUsers = path("activeUsers") {
    def inner = DB.db.readOnly { implicit s =>
      val res = Users.active map (_.publicJson)
      // Don't do the action inside complete
      // Because... I don't fucking know, but it leaks DB connections and that's BAD
      complete(JArray(res))
    }
    // Need to wrap inner inside a lambda to force recalculation on each request
    (get | post).apply(ctx => inner(ctx))
  }

  val getUser = path(IntNumber) { id =>
    val userId = Id[User](id)
    get {
      withAuth { u =>
        handleError(doGetUser(userId)) { res =>
          complete(res)
        }
      }
    }
  }

  val userRoute = activeUsers ~ pathPrefix("user") { getUser }
}