package money.models

import scala.slick.driver.PostgresDriver.simple.{ Database => SlickDatabase, _ }
import scala.slick.ast.{ ColumnOption => O }
import money.db.annotations._
import money.db._
import DBSession._

import org.json4s._
import JsonDSL._

import money.errors._

@model
case class User(@column("id", O.PrimaryKey, O.Nullable, O.AutoInc) id: Option[Id[User]],
                @column("login", O.NotNull) login: String,
                @column("password_hash", O.NotNull) passwordHash: String,
                @column("password_salt", O.NotNull) passwordSalt: String,
                @column("name", O.NotNull) name: String,
                @column("email", O.NotNull) email: String,
                @column("admin", O.NotNull) admin: Boolean,
                @column("active", O.NotNull) active: Boolean) extends Model[User] {

  def publicJson: JObject =
    ("id" -> id) ~
    ("name" -> name) ~
    ("email" -> email)

  def privateJson: JObject =
    publicJson ~
    ("login" -> login) ~
    ("admin" -> admin) ~
    ("active" -> active)
}

object User extends ModelCompanion[User]

@repo[User]("users")
@index("login_idx", "login", unique = true)
@index("email_idx", "email", unique = true)
object Users extends ModelRepo[User] {
  def active(implicit s: ROSession) =
    (for (u <- this.tableQuery if u.active === true) yield u).list
}
