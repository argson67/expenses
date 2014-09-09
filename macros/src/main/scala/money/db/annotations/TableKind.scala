package money.db.annotations

import scala.annotation.StaticAnnotation

sealed abstract class TableKind {
  def parentName: String
  def companionParentName: String
  def annotationName: String
  def annotationTag: String

  def repoName: String
  def tableParentName: String
}

object TableKind {
  def apply(annotation: String): Option[TableKind] = annotation match {
    case TableRelation.annotationName | TableRelation.annotationTag => Some(TableRelation)
    case TableModel.annotationName    | TableModel.annotationTag    => Some(TableModel)
    case _                                                          => None
  }
}

case object TableRelation extends TableKind {
  val parentName: String = "Relation"
  val companionParentName: String = "RelationCompanion"
  val annotationName: String = "relation"
  val annotationTag: String = "relationTag"

  val repoName: String = "RelationRepo"
  val tableParentName: String = "Table"
}

case object TableModel extends TableKind {
  val parentName: String = "Model"
  val companionParentName: String = "ModelCompanion"
  val annotationName: String = "model"
  val annotationTag: String = "modelTag"

  val repoName: String = "ModelRepo"
  val tableParentName: String = "ModelTable"
}
