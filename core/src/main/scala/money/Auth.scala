package money

import scala.concurrent.duration._

import org.joda.time.DateTime

import money.errors._
import money.json._
import money.data._

import money.json.JsonMacros._

import money.db.Id
import money.models.{ User, Users }

import com.github.t3hnar.bcrypt._
import spray.http.HttpCookie
import spray.routing.Directive1

trait Auth extends Sessions {
  self: MoneyService =>

  private val authCookieName: String = "sessionID"

  def withAuth = optionalCookie(authCookieName) flatMap { cookie =>
    for (session <- option2Result(cookie) toResult AuthError("Must be logged in");
         id      <- findLogin(session.content);
         u       <- DB.db.readOnly { implicit s =>
           Users.getById(id)
         })
    yield timer("withAuth")(u)
  }

  def noAuth = optionalCookie(authCookieName) flatMap { cookie =>
    result2directive(cookie match {
      case Some(_) => Bad(AuthError("You are already logged in!"))
      case None    => Good(())
    })
  }

  private def checkPwd(u: User, pwd: String): Result[Unit] = {
    val hash = pwd.bcrypt(u.passwordSalt)
    if (hash == u.passwordHash) Good(())
    else Bad(AuthError(s"Incorrect password for user '${u.name}'"))
  }

  private def processCredentials(expirationDate: DateTime)(creds: Credentials): Result[(String, User)] =
    DB.db.readOnly { implicit s =>
      for (u <- Users.getByLogin(creds.username);
           _ <- checkPwd(u, creds.password);
           s <- createSession(u.id.get, expirationDate))
      yield (s, u)
    }

  private def processReg(reg: RegistrationForm) =
    DB.db.readWrite { implicit s =>
      val salt = generateSalt
      Users.save(User(None, reg.username, reg.password.bcrypt(salt), salt, reg.name, reg.email, admin = false, active = true))
    }

  private val loginRoute = path("login") {
    post {
      noAuth { _ =>
      val ej = extractJson[Credentials]
        ej { creds =>
          val expirationDate = DateTime.now().withDurationAdded(1.day.toMillis, 30)
          val cookieDate = spray.http.DateTime(expirationDate.getMillis)
          handleError(creds flatMap processCredentials(expirationDate)) { case (s, u) =>
            setCookie(HttpCookie(authCookieName, s, Some(cookieDate))) {
              complete(u.privateJson)
            }
          }
        }
      }
    }
  }

  private val logoutRoute = path("logout") {
    (get | post) {
      withAuth { u =>
        handleError(deleteSession(u.id.get)) { _ =>
          deleteCookie(authCookieName) {
            complete("OK")
          }
        }
      }
    }
  }

  private val registerRoute = path("register") {
    post {
      val ej = extractJson[RegistrationForm]
      ej (_ flatMap processReg)
    }
  }

  private val whoAmI = path("whoami") {
    get {
      withAuth { u =>
        complete(u.publicJson)
      }
    }
  }

  val authRoute = loginRoute ~ logoutRoute ~ registerRoute ~ whoAmI
}
