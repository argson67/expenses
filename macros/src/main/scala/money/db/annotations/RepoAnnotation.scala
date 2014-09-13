package money.db.annotations

// Scala imports
import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.annotation.StaticAnnotation

class repo[T](n: String) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro RepoAnnotation.impl
}

object RepoAnnotation {
  def impl(c1: Context)(annottees: c1.Expr[Any]*): c1.Expr[Any] = {
    val h = new { val c: c1.type = c1 } with RepoAnnotationHelper

    import c1.universe._

    val inputs = annottees.map(_.tree).toList

    // Extract the table name
    val q"new repo[$table]($nameLit)" = c1.prefix.tree
    val Literal(Constant(sqlTableName: String)) = nameLit

    // Extract column info from the table
    val tableTpe = c1.typeCheck(q"(().asInstanceOf[$table])").tpe

    if (!tableTpe.typeSymbol.isClass) {
      c1.error(table.pos, "Model type argument must be a class name.")
    }

    val outputs: List[Tree] = inputs map {
      // TODO: Check if type argument to DBRepo is correct
      case o@q"..$mods object $repoName extends ..$parents { ..$body }" =>
        val tableName = tableTpe.typeSymbol.name

        val fields = h.getFields(tableTpe, mods.annotations)

        val expectedRepoName = tableName.toString + "s"
        if (repoName.toString != expectedRepoName) {
          c1.error(o.pos, s"Repo name for table '$tableName' must be '$expectedRepoName'")
        }

        val tableKind = h.getTableKind(tableTpe)

        h.verifyParents(parents, tableKind) foreach { err => c1.error(c1.enclosingPosition, err) }

        val table = h.genTable(sqlTableName, repoName, tableName.toTypeName, fields, tableKind)
        val tableQuery = h.genTableQuery(repoName)
        val tableNameField = h.genTableName(sqlTableName)
        val holds = h.genHolds(fields filter (_.isColumn), tableName.toTypeName)
        val rType = h.genRType(repoName)
        val newBody: List[Tree] =
          body ++ List(table, tableQuery, tableNameField, rType, holds) ++
            h.genFindBys(fields filter (_.isColumn), tableName.toTypeName) ++
            h.genGetBys(fields filter (_.isColumn), tableName.toTypeName) ++
            h.genDeleteBys(fields filter (_.isColumn), tableName.toTypeName)
        val res = q"object $repoName extends ..$parents { ..$newBody }"

        //c1.echo(c1.enclosingPosition, s"Generated Repo:\n $res")

        res
      case x =>
        c1.error(x.pos, "A repo must be singleton that does not extend any classes or traits")
        EmptyTree
    }

    c1.Expr(Block(outputs, Literal(Constant())))
  }
}

