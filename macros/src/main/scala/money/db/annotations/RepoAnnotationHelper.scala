package money.db.annotations

import scala.language.experimental.macros
import scala.reflect.macros.Context

trait RepoAnnotationHelper {
  val c: Context

  import c.universe._

  abstract class Field {
    def sym: Symbol
    def isColumn: Boolean = false
  }

  case class ColumnField(sym: TermSymbol, options: List[Tree]) extends Field {
    override def isColumn = true
  }

  // Die if the full name is incorrect or changes
  val fka = c.mirror.staticModule("scala.slick.model.ForeignKeyAction")

  case class ForeignKeyField(sym: TermSymbol,
                             name: String,
                             colName: TermName,
                             repo: Tree,
                             repoColName: TermName,
                             onUpdate: Tree = q"$fka.NoAction",
                             onDelete: Tree = q"$fka.Cascade") extends Field
  case class IndexField(sym: TermSymbol,
                        name: String,
                        on: Tree,
                        unique: Boolean) extends Field
  case object ErrorField extends Field {
    val sym = NoSymbol
  }

  def verifyParents(parents: List[Tree], tableKind: TableKind): Option[String] = {
    val requiredParent = tableKind.repoName

    val res = parents.exists {
      case AppliedTypeTree(Ident(parentName), _) if parentName.toString == requiredParent =>
        true
      case AppliedTypeTree(Select(_, parentName), _) if parentName.toString == requiredParent =>
        true
      case _ =>
        false
    }

    if (res) None else Some(s"'$requiredParent' is a required parent.")
  }

  def getColumns(tpe: Type): List[Field] = {
    val cons = tpe.member(nme.CONSTRUCTOR)
    val consParams = cons.asMethod.paramss.head
    val members = consParams map (_.asTerm)

    val cols = members map { m => m.annotations match {
      case a :: Nil => if (a.tpe =:= typeOf[column]) {
        ColumnField(m, a.scalaArgs)
      } else {
        c.error(m.pos, "Unexpected annotation, not column")
        ErrorField
      }
      case _ =>
        c.error(m.pos, "Only one annotation expected")
        ErrorField
    }}

    cols
  }

  def getKeys(annotations: List[Tree]): List[Field] = {
    val fkname = newTypeName("foreignKey")
    val idxname = newTypeName("index")

    // TODO: Use quasiquotes for this pattern match?

    c.echo(c.enclosingPosition, showRaw(annotations))

    val keys: List[Option[Field]] = annotations map {
      case Apply(Select(New(Ident(`fkname`)), nme.CONSTRUCTOR),
      Literal(Constant(fieldName: String)) ::
        Literal(Constant(name: String)) ::
        Literal(Constant(colName: String)) ::
        Ident(repoName) ::
        Literal(Constant(repoColName: String)) ::
        (args: List[Tree])) =>

        // TODO: Process args (onUpdate, onDelete)

        val sym = NoSymbol.newTermSymbol(newTermName(fieldName))
        Some(ForeignKeyField(sym, name, newTermName(colName), Ident(repoName), newTermName(repoColName)))
      case Apply(Select(New(Ident(`idxname`)), nme.CONSTRUCTOR),
      Literal(Constant(name: String)) ::
        (on: Tree) ::
        (args: List[Tree])) =>

        // Yay, typecheck. Kinda. Sorta.
        val onT: Tree = on match {
          case q"(..$cols)" =>
            val colIds = cols map {
              case Literal(Constant(n: String)) => Ident(newTermName(n))
              case other =>
                c.error(c.enclosingPosition, s"The second argument to index must be a string or a tuple of strings")
                EmptyTree
            }
            q"(..$colIds)"
          case Literal(Constant(n: String)) =>
            Ident(newTermName(n))
          case other =>
            c.error(c.enclosingPosition, s"The second argument to index must be a string or a tuple of strings")
            EmptyTree
        }

        val uniqueName = newTermName("unique")

        val unique = args match {
          case List(Literal(Constant(b: Boolean))) => b
          case List(AssignOrNamedArg(Ident(`uniqueName`), Literal(Constant(b: Boolean)))) => b
          case _ => false
        }

        val sym = NoSymbol.newTermSymbol(newTermName(name))

        Some(IndexField(sym, name, onT, unique))
      case other =>
        c.echo(other.pos, s"Unexpected annotation on a repo: $other")
        None
    }

    keys.flatten
  }

  def getFields(tpe: Type, annotations: List[Tree]): List[Field] =
    getColumns(tpe) ++ getKeys(annotations)

  def getTableKind(tpe: Type): TableKind = {
    def helper(annotations: List[Annotation]): Option[TableKind] = annotations match {
      case Nil =>
        None
      case a :: as =>
        TableKind(a.tpe.typeSymbol.name.toString) match {
          case Some(tk) => Some(tk)
          case _ => helper(as)
        }
    }

    c.echo(c.enclosingPosition, s"Type symbol: ${tpe.typeSymbol}. Annotations: ${tpe.typeSymbol.annotations}")

    helper(tpe.typeSymbol.annotations) getOrElse {
      c.error(c.enclosingPosition, "Corresponding table not properly annotated")
      null
    }
  }

  def genTable(tableName: String,
               repoName: TermName,
               modelName: TypeName,
               fields: List[Field],
               tableKind: TableKind): c.Tree = {
    def genColumn(a: ColumnField): Tree = {
      val opt = typeOf[Option[Any]].asInstanceOf[TypeRef].sym

      val tpe = a.sym.typeSignature match {
        case TypeRef(_, `opt`, x) => x.head
        case other => other
      }

      val name = a.sym.name.toTermName

      if (a.sym.name.toString == "id") {
        q"def $name = column[$tpe](..${a.options})"
      } else {
        q"def $name = column[$tpe](..${a.options})"
      }
    }

    def genIndex(a: IndexField): Tree = {
      val idxName = Literal(Constant(a.name))
      val idxUnique = Literal(Constant(a.unique))
      val name = a.sym.name.toTermName

      q"def $name = index($idxName, ${a.on}, $idxUnique)"
    }

    def genFKey(a: ForeignKeyField): Tree = {
      val fkName: Tree = Literal(Constant(a.name))
      val name: TermName = a.sym.name.toTermName

      q"def $name = foreignKey($fkName, ${a.colName}, ${a.repo}.tableQuery)(_.${a.repoColName}, ${a.onUpdate}, ${a.onDelete})"
    }

    val genStar: Tree = {
      val cols: List[Tree] = fields filter (_.isColumn) map { f =>
        val sym = f.sym
        if (sym.typeSignature <:< typeOf[Option[Any]]) q"${sym.name.toTermName}.?" else Ident(sym.name)
      }

      val consModel = q"${modelName.toTermName}.tupled"
      val deconsModel = q"${modelName.toTermName}.unapply"

      q"def * = (..$cols) <> ($consModel, $deconsModel)"
    }

    val body: List[Tree] = (fields map {
      case ac: ColumnField => genColumn(ac)
      case fk: ForeignKeyField => genFKey(fk)
      case idx: IndexField => genIndex(idx)
      case other => // error
        EmptyTree
    }) :+ genStar

    val name = newTypeName(repoName + "Table")

    val tableNameStr = Literal(Constant(tableName))

    val tableParent = newTypeName(tableKind.tableParentName)
    q"protected class $name(tag: Tag) extends $tableParent[$modelName](tag, $tableNameStr) { ..$body }"
  }

  def genTableQuery(repoName: TermName): Tree = {
    val name = newTypeName(repoName + "Table")
    q"val tableQuery = TableQuery[$name]"
  }

  def genTableName(tableName: String): Tree = {
    val nameStr = Literal(Constant(tableName))
    q"val tableName = $nameStr"
  }

  def genRType(repoName: TermName): Tree = {
    val name = newTypeName(repoName + "Table")
    q"type R = $name"
  }

  def getColTpes(cols: List[Field]): List[(TermName, Type)] = cols map { c =>
    val colName = c.sym.name
    val opt = typeOf[Option[Any]].asInstanceOf[TypeRef].sym

    val colTpe = c.sym.typeSignature match {
      case TypeRef(_, `opt`, x) => x.head
      case other => other
    }
    (colName.toTermName, colTpe)
  }

  def genFindBys(cols: List[Field], modelName: TypeName): List[Tree] = getColTpes(cols) map {
    case (name, tpe) =>
      val findName = newTermName("findBy" + name.toString.capitalize)
      q"def $findName(v: $tpe)(implicit s: RSession): List[$modelName] = findBy(_.$name)(v)"
  }

  def genGetBys(cols: List[Field], modelName: TypeName): List[Tree] = getColTpes(cols) map {
    case (name, tpe) =>
      val getName = newTermName("getBy" + name.toString.capitalize)
      q"def $getName(v: $tpe)(implicit s: RSession): Result[$modelName] = getBy(_.$name)(v)"
  }

  def genHolds(cols: List[Field], modelName: TypeName) = {
    assert(cols.size > 0)

    val conds = cols map (_.sym.name.toTermName) map { name =>
      q"row.$name === rel.$name"
    }
    val cond = conds.reduce((a, b) => q"$a && $b")
    val select = q"tableQuery.filter(row => $cond)"
    val body = q"$select.list.headOption.isDefined"
    q"def holds(rel: $modelName)(implicit s: RSession) = $body"
  }
}
