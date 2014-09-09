package money.db.annotations

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.annotation.StaticAnnotation

abstract class TableAnnotation extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro TableAnnotation.impl
}

object TableAnnotation {
  private def getTableKind(c: Context)(prefixTree: c.Tree) = {
    import c.universe._

    def get(name: String): TableKind = TableKind(name) match {
      case Some(tk) =>
        tk
      case None =>
        c.error(c.enclosingPosition, s"Wrong annotation: $name")
        null
    }

    prefixTree match {
      case Apply(Select(New(Ident(annot)), nme.CONSTRUCTOR), List()) =>
        get(annot.toString)
      case Apply(Select(New(Select(_, annot)), nme.CONSTRUCTOR), List()) =>
        get(annot.toString)
      case _ =>
        c.error(c.enclosingPosition, s"Malformed annotation: ${showRaw(c.prefix)}")
        null
    }
  }

  private def gatherAnnottees(c: Context)(annottees: Seq[c.Expr[Any]], tableKind: TableKind): (c.Tree, c.Tree) = {
    import c.universe._

    val annotationName = tableKind.annotationName

    val (classDef, objDef) = annottees.foldLeft((EmptyTree: Tree, EmptyTree: Tree)) { (soFar, a) => (soFar, a) match {
      case ((cd, od), ann) =>
        ann.tree match {
          case c: ClassDef => (c, od)
          case m: ModuleDef => (cd, m)
          case other =>
            c.error(other.pos, s"$annotationName cannot annotate '$other'")
            soFar
        }
    }}

    if (classDef == EmptyTree) {
      c.error(c.enclosingPosition, s"$annotationName must have a case class definition")
    }

    if (objDef == EmptyTree) {
      c.error(c.enclosingPosition, s"$annotationName must have a companion object")
    }

    (classDef, objDef)
  }

  private def getColumns(c: Context)(body: List[c.Tree]): List[c.Tree] = {
    import c.universe._

    body match {
      case Nil =>
        c.error(c.enclosingPosition, "No constructor???")
        Nil
      case DefDef(_, nme.CONSTRUCTOR, _, List(params), _, _) :: _ =>
        params: List[c.Tree]
      case s :: stmts =>
        getColumns(c)(stmts)
    }
  }

  private def verifyParents(c: Context)(parents: List[c.Tree], tableKind: TableKind, isCompanion: Boolean): Option[String] = {
    import c.universe._

    val requiredParent = if (isCompanion) tableKind.companionParentName else tableKind.parentName

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

  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    c.echo(c.enclosingPosition, s"Number of annottees: ${annottees.size}\n${annottees map (a => show(a.tree))}")

    val _tableKind = getTableKind(c)(c.prefix.tree)
    val (classTree, companionTree) = gatherAnnottees(c)(annottees, _tableKind)

    val (newClass, newCompanion) = (classTree, companionTree) match {
      case (ClassDef(classMods,
                     className,
                     classTParams,
                     Template(classParents,
                              classSelf,
                              classBody)),
            ModuleDef(coMods,
                      coName,
                      Template(coParents,
                               coSelf,
                               coBody))) =>
        if (!classMods.hasFlag(Flag.CASE)) {
          c.error(c.enclosingPosition, "Table description must be a case class")
        }

        if (className.toString != coName.toString) {
          c.error(c.enclosingPosition, s"Names don't match: $className, $coName")
        }

        verifyParents(c)(classParents, _tableKind, isCompanion = false) match {
          case Some(err) =>
            c.error(c.enclosingPosition, err)
          case _ =>
        }

        verifyParents(c)(coParents, _tableKind, isCompanion = true) foreach { err => c.error(c.enclosingPosition, err) }

        val _columns = getColumns(c)(classBody)

        val helper = new {
          val ctx: c.type = c
          val name: TypeName = className
          val columns: List[Tree] = _columns
          val tableKind: TableKind = _tableKind
        } with TableAnnotationHelper

        def addAnnotation(mods: Modifiers, annot: Tree): Modifiers =
          Modifiers(mods.flags, mods.privateWithin, annot :: mods.annotations)

        val newClassMods =
          addAnnotation(classMods, Apply(Select(New(Ident(newTypeName(_tableKind.annotationTag))), nme.CONSTRUCTOR), Nil))

        val nClass = ClassDef(newClassMods,
                              className,
                              classTParams,
                              Template(classParents,
                                       classSelf,
                                       classBody ++ helper.genClassBody))
        val nComp = ModuleDef(coMods,
                              coName,
                              Template(coParents,
                                       coSelf,
                                       coBody ++ helper.genCompanionBody))

        (nClass, nComp)

      case other =>
        c.error(c.enclosingPosition, "")
        (EmptyTree, EmptyTree)
    }

    c.Expr(Block(List(newClass, newCompanion), Literal(Constant(()))))
  }
}
