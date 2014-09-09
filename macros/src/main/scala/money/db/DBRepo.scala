package money.db

// Slick imports
import scala.slick.driver.PostgresDriver.simple.{
  tableQueryToTableQueryExtensionMethods => tq2tqem,
  _
}
import scala.slick.driver.PostgresDriver.SchemaDescription
import scala.slick.lifted.{Query, TableQuery}
import scala.slick.jdbc.meta.MTable
import scala.slick.ast.BaseTypedType
import money.errors._

// Local
import DBSession._

trait DBRepo[M] {
  type R <: Table[M]
  def tableQuery: TableQuery[R]
  def tableName: String

  def ddl(implicit db: Database): SchemaDescription = db.readOnly { implicit s =>
    tq2tqem(tableQuery).ddl // Dunno why IDEA can't figure out this implicit. Adding it explicitly.
  }

  def createStmts(implicit db: Database): String = ddl.createStatements mkString "\n"

  def dropStmts(implicit db: Database): String = ddl.dropStatements mkString "\n"

  def create(implicit db: Database): Boolean = db.readOnly { implicit s =>
    if (MTable.getTables(tableName).list().isEmpty) {
      ddl.create
      true
    } else {
      false
    }
  }

  def drop(implicit db: Database): Boolean = db.readOnly { implicit s =>
    if (MTable.getTables(tableName).list().isEmpty) {
      false
    } else {
      ddl.drop
      true
    }
  }

  @inline
  protected def findBy[T](c: R => Column[T])(v: T)(implicit s: RSession, ev: BaseTypedType[T]): List[M] =
    (for (t <- tableQuery if columnExtensionMethods(c(t)) === v) yield t).list

  @inline
  def deleteBy[T](c: R => Column[T])(v: T)(implicit s: RWSession, ev: BaseTypedType[T]): Int =
    (for (t <- tableQuery if columnExtensionMethods(c(t)) === v) yield t).delete

  @inline
  protected def getBy[T](c: R => Column[T])(v: T)(implicit s: RSession, ev: BaseTypedType[T]): Result[M] =
    (for (t <- tableQuery if columnExtensionMethods(c(t)) === v) yield t).firstOption.toResult(DBError(s"Cannot find value '$v'"))

  def count(implicit session: RSession): Int = Query(tableQuery.length).first

  def all(implicit session: RSession): Seq[M] = tableQuery.map(t => t).list
}
