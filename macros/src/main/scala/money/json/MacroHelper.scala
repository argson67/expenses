package money.json

import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.macros.Context

trait MacroHelper {
  val ctx: Context

  import ctx.universe._

  def typeSym[T: TypeTag] = typeOf[T].typeSymbol
  def typeSym1[T[_]](implicit ev: TypeTag[T[Any]]) = typeOf[T[Any]] match {
    case TypeRef(_, t, _) => t
  }
}