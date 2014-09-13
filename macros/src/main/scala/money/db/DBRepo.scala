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

  protected def timer[R](name: String)(block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    val ms = (t1 - t0) / 1000000
    println(s"Elapsed time in '$name': $ms ms")
    result
  }

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
  protected def findBy[T](c: R => Column[T])(implicit s: RSession, ev: BaseTypedType[T]): T => List[M] = {
    def query(_v: Column[T]) = for (t <- tableQuery if columnExtensionMethods(c(t)) === _v) yield t
    val compiled = Compiled(query _)
    (v: T) => compiled(v).list
  }

  @inline
  def deleteBy[T](c: R => Column[T])(implicit s: RWSession, ev: BaseTypedType[T]): T => Int = {
    def query(_v: Column[T]) = for (t <- tableQuery if columnExtensionMethods(c(t)) === _v) yield t
    val compiled = Compiled(query _)
    (v: T) => compiled(v).delete
  }

  @inline
  protected def getBy[T](c: R => Column[T])(implicit s: RSession, ev: BaseTypedType[T]): T => Result[M] = {
    def query(_v: Column[T]) = for (t <- tableQuery if columnExtensionMethods(c(t)) === _v) yield t
    val compiled = Compiled(query _)
    (v: T) => compiled(v).firstOption.toResult(DBError(s"Cannot find value '$v'"))
  }

  def count(implicit session: RSession): Int = Compiled(Query(tableQuery.length)).first

  private lazy val compiledAll = Compiled(tableQuery.map(t => t))

  def all(implicit session: RSession): List[M] = compiledAll.list
}
