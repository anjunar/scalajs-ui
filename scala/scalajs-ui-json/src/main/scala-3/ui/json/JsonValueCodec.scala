package ui.json

import java.util
import scala.scalajs.js
import java.math.{BigDecimal as JavaBigDecimal}

private[json] object JsonValueCodec {

  /** Exact decimals, e.g. prices. Scala's `BigDecimal` is an alias of `scala.math.BigDecimal`. */
  val DecimalTypes: Set[String] = Set(
    "scala.math.BigDecimal",
    "scala.BigDecimal",
    "scala.package.BigDecimal",
    "java.math.BigDecimal"
  )

  /** Beyond this a JavaScript number has no exact integers: a Long outside it travels as a string
    * of digits, like a decimal.
    */
  private val MaxSafeInteger = 9007199254740991L

  def serializePrimitive(value: Any, typeName: String): js.Any =
    typeName match {
      // Written as a string of digits: a JavaScript number would round them and drop the scale (120.00 to 120).
      case "java.math.BigDecimal" =>
        value.asInstanceOf[JavaBigDecimal].toPlainString
      case decimal if DecimalTypes.contains(decimal) =>
        value.asInstanceOf[BigDecimal].bigDecimal.toPlainString
      case "java.util.UUID" =>
        value.asInstanceOf[util.UUID].toString
      case "scala.Int" | "int" =>
        value.asInstanceOf[Int].toDouble
      case "scala.Double" | "double" =>
        value.asInstanceOf[Double]
      case "scala.Float" | "float" =>
        value.asInstanceOf[Float].toDouble
      case "scala.Long" | "long" =>
        val number = value.asInstanceOf[Long]
        if (number >= -MaxSafeInteger && number <= MaxSafeInteger) number.toDouble
        else number.toString
      case "scala.Short" | "short" =>
        value.asInstanceOf[Short].toDouble
      case "scala.Byte" | "byte" =>
        value.asInstanceOf[Byte].toDouble
      case "scala.Boolean" | "boolean" =>
        value.asInstanceOf[Boolean]
      case "scala.Char" | "char" =>
        value.toString
      case _ =>
        value.toString
    }

  /** The JSON type is checked before the cast. The casts are unchecked under the optimizer's
    * semantics, so a string where a number is declared would otherwise not fail but read as
    * garbage.
    */
  def deserializePrimitive(value: js.Any, typeName: String): Any =
    typeName match {
      case "scala.Int" | "int"         => number(value).toInt
      case "scala.Double" | "double"   => number(value)
      case "scala.Float" | "float"     => number(value).toFloat
      case "scala.Long" | "long"       => long(value)
      case "scala.Short" | "short"     => number(value).toShort
      case "scala.Byte" | "byte"       => number(value).toByte
      case "scala.Boolean" | "boolean" => boolean(value)
      case "scala.Char" | "char"       =>
        string(value).headOption.getOrElse('\u0000')
      case "scala.Predef.String" | "java.lang.String" =>
        string(value)
      case "java.util.UUID" =>
        util.UUID.fromString(uuidValue(string(value)))
      case "java.math.BigDecimal" =>
        decimal(value)
      case decimalType if DecimalTypes.contains(decimalType) =>
        BigDecimal(decimal(value))
      case _ =>
        value
    }

  private def number(value: js.Any): Double =
    js.typeOf(value) match {
      case "number" => value.asInstanceOf[Double]
      case other    => throw new IllegalArgumentException(s"Expected a number, got $other")
    }

  /** Written as a number inside the safe integer range and as a string of digits beyond it; both
    * are read, whichever range the value is in.
    */
  private def long(value: js.Any): Long =
    js.typeOf(value) match {
      case "number" => value.asInstanceOf[Double].toLong
      case "string" =>
        try value.asInstanceOf[String].trim.toLong
        catch {
          case _: NumberFormatException =>
            throw new IllegalArgumentException(s"Expected an integer, got '$value'")
        }
      case other => throw new IllegalArgumentException(s"Expected an integer, got $other")
    }

  private def boolean(value: js.Any): Boolean =
    js.typeOf(value) match {
      case "boolean" => value.asInstanceOf[Boolean]
      case other     => throw new IllegalArgumentException(s"Expected a boolean, got $other")
    }

  private def string(value: js.Any): String =
    js.typeOf(value) match {
      case "string" => value.asInstanceOf[String]
      case other    => throw new IllegalArgumentException(s"Expected a string, got $other")
    }

  /** A decimal as a string of digits keeps them all; as a JSON number it is what `JSON.parse` made
    * of it, a double, whose shortest representation is read back, e.g. 120.5 as 120.5, with the
    * scale of those digits.
    */
  private def decimal(value: js.Any): JavaBigDecimal =
    js.typeOf(value) match {
      case "number" => new JavaBigDecimal(value.toString)
      case "string" => new JavaBigDecimal(value.asInstanceOf[String].trim)
      case other    => throw new IllegalArgumentException(s"Expected a decimal number, got $other")
    }

  def asObject(value: js.Any): js.Dictionary[js.Any] =
    if (
      value != null &&
      !js.isUndefined(value) &&
      !js.Array.isArray(value) &&
      js.typeOf(value) == "object"
    ) value.asInstanceOf[js.Dictionary[js.Any]]
    else throw new IllegalArgumentException(s"Expected JSON object, got ${js.typeOf(value)}")

  def asArray(value: js.Any): js.Array[js.Any] =
    if (js.Array.isArray(value)) value.asInstanceOf[js.Array[js.Any]]
    else throw new IllegalArgumentException(s"Expected JSON array, got ${js.typeOf(value)}")

  private def uuidValue(raw: String): String = {
    val value = Option(raw).getOrElse("").trim
    if (value.contains("/")) value.split('/').lastOption.getOrElse(value)
    else value
  }
}
