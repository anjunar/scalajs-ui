package ui.json

import ui.core.state.{ListProperty, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

import java.util
import scala.annotation.meta.field
import scala.collection.immutable.ListMap
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.reflect.Reflect
import scala.scalajs.reflect.annotation.EnableReflectiveInstantiation

final class ValueTypes(
    var optional: Property[Option[String]] = Property(None),
    var identifiers: Property[ListMap[String, util.UUID]] = Property(ListMap.empty),
    var numbers: Property[List[Int]] = Property(Nil),
    var array: Property[Array[Int]] = Property(Array.empty),
    var jsArray: Property[js.Array[Int]] = Property(js.Array()),
    var flags: Property[Set[Boolean]] = Property(Set.empty),
    var longNumber: Property[Long] = Property(0L),
    var floatNumber: Property[Float] = Property(0f),
    var shortNumber: Property[Short] = Property(0.toShort),
    var byteNumber: Property[Byte] = Property(0.toByte),
    var character: Property[Char] = Property('\u0000'),
    var raw: Property[js.Any] = Property(null),
    var replies: Property[Array[Reply]] = Property(Array.empty)
)
