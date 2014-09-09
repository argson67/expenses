package money.db

abstract class SqlTable[T <: SqlTable[T]] {

}

trait SqlTableCompanion[T <: SqlTable[T]] {
  def repo: DBRepo[T]

  def desc: List[String]

  def tableName: String
}
