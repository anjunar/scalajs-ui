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
        value.asInstanceOf[Long].toDouble
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

  def deserializePrimitive(value: js.Any, typeName: String): Any =
    typeName match {
      case "scala.Int" | "int"         => value.asInstanceOf[Double].toInt
      case "scala.Double" | "double"   => value.asInstanceOf[Double]
      case "scala.Float" | "float"     => value.asInstanceOf[Double].toFloat
      case "scala.Long" | "long"       => value.asInstanceOf[Double].toLong
      case "scala.Short" | "short"     => value.asInstanceOf[Double].toShort
      case "scala.Byte" | "byte"       => value.asInstanceOf[Double].toByte
      case "scala.Boolean" | "boolean" => value.asInstanceOf[Boolean]
      case "scala.Char" | "char"       =>
        value.toString.headOption.getOrElse('\u0000')
      case "java.util.UUID" =>
        util.UUID.fromString(uuidValue(value.toString))
      case "java.math.BigDecimal" =>
        decimal(value)
      case decimalType if DecimalTypes.contains(decimalType) =>
        BigDecimal(decimal(value))
      case _ =>
        value
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
