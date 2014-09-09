package money.db.annotations

// Scala imports
import scala.annotation.StaticAnnotation

// Slick imports
import scala.slick.model.ForeignKeyAction

case class foreignKey(field: String,
                      n: String,
                      colName: String,
                      repo: Any,
                      repoColName: String,
                      onUpdate: ForeignKeyAction = ForeignKeyAction.NoAction,
                      onDelete: ForeignKeyAction = ForeignKeyAction.Cascade) extends StaticAnnotation
