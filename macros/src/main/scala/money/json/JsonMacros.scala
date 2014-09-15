package money.json

import scala.language.experimental.macros
import scala.reflect.macros.{TypecheckException, Context}
import scala.reflect.runtime.{ universe => ru }

object JsonMacros {
  implicit def caseClassFormat[T <: Product with Serializable]: JsonFormat[T] = macro impl[T]

  def impl[T <: Product with Serializable](c: Context)(implicit wtt: c.WeakTypeTag[T]): c.Expr[JsonFormat[T]] = {
    import c.universe._

    //val helper = new { val ctx: c.type = c } with MacroHelper
    //import helper._

    //c.echo(c.enclosingPosition, "In macro")

    val tpe = weakTypeOf[T]
    val cons = tpe.member(nme.CONSTRUCTOR)
    val consParams = cons.asMethod.paramss.head

    val enums = consParams map { param =>
      /*val xxx = c.typeCheck(q"().asInstanceOf[money.json.JsonFormat[${param.typeSignature}]]").tpe
      try {
        println(s"Looking up implicit value for type JsonFormat[${param.typeSignature}}]")
        c.inferImplicitValue(xxx)
      } catch {
        case e: TypecheckException =>
          println(s"Cannot find implicit value for type JsonFormat[${param.typeSignature}]")
      }*/

      val nameTree = Literal(Constant(param.name.toString))
      fq"${param.name} <- jo.select[${param.typeSignature}]($nameTree)"
    }

    val readImpl = q"for (..$enums) yield new $tpe(..${consParams map (_.name)})"

    val _read = q"val _read: PartialFunction[JValue, Result[$tpe]] = { case jo: JObject => $readImpl }"
    val fields = consParams map { param =>
      val nameTree = Literal(Constant(param.name.toString))
      q"$nameTree -> x.${param.name.toTermName}.toJson"
    }
    val write = q"def write(x: $tpe) = JObject(..$fields)"

    val _typeName = Literal(Constant(tpe.toString))

    val typeName = q"val typeName = ${_typeName}"

    val res = q"""
      {
        import org.json4s.JsonAST. {JValue, JObject }
        import money.json.{ JValueFinder, JsonOps, JsonFormat }
        import money.errors.Result
        import scala.reflect.runtime. { universe => ru }
        new JsonFormat[${tpe.typeSymbol}] { $typeName; ${_read}; $write }
      }"""

    c.echo(c.enclosingPosition, s"Result: ${show(res)}")

    c.Expr[JsonFormat[T]](res)
  }
}
