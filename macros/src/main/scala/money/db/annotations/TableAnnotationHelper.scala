package money.db.annotations

import scala.language.experimental.macros
import scala.reflect.macros.Context

trait TableAnnotationHelper {
  val ctx: Context

  import ctx.universe._

  val name: TypeName
  val columns: List[Tree]
  val tableKind: TableKind

  val (colNames: List[TermName], colTpes: List[Tree]) = {
    (columns map { (t: Tree) => t match {
      case ValDef(_, colName, colTpe, _) =>
        (colName: TermName, colTpe: Tree)
      case other => ctx.error(other.pos, s"Malformed column: $other")
        (newTermName(""): TermName, EmptyTree: Tree)
    }}).unzip
  }

  def genClassBody: List[Tree] = {
    def withId: Tree = {
      /*
       * ASSUMPTIONS:
       * - id is the very first column
       * - no other column is called "newId"
       */

      val nonIdColumns: List[Tree] = colNames.tail map (s => Ident(s))
      val newIdName = newTermName("newId")
      val cols: List[Tree] = q"Some($newIdName)" :: nonIdColumns
      q"def withId($newIdName: Id[$name]): $name = ${name.toTermName}(..$cols)"
    }

    tableKind match {
      case TableModel    => List(withId)
      case TableRelation => Nil
    }
  }

  def genCompanionBody: List[Tree] = {
    val repoName = newTermName(name.toString + "s")
    val repo: Tree = q"val repo = $repoName"

    val genTupled: Tree = {
      val colsPat = colNames map { c =>
        Bind(c, Ident(nme.WILDCARD))
      }
      val cols = colNames map (Ident(_))

      q"""def tupled(tuple : (..$colTpes)): $name = tuple match {
        case (..$colsPat) => ${name.toTermName}(..$cols) } """
    }

    val genDesc: Tree = {
      val cols = columns map { c => Literal(Constant(c.toString)) }
      q"""val desc: List[String] = List(..$cols)"""
    }

    val genModelName: Tree = {
      val mn = Literal(Constant(name.toString))
      q"""val tableName: String = $mn"""
    }

    List(repo, genDesc, genTupled, genModelName)
  }
}
