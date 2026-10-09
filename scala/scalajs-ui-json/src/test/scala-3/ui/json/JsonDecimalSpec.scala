package ui.json

import ui.core.state.{ListProperty, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

import java.util.UUID
import scala.annotation.meta.field
import scala.collection.immutable.ListMap
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.reflect.Reflect
import scala.scalajs.reflect.annotation.EnableReflectiveInstantiation

class JsonDecimalSpec extends AnyFlatSpec with Matchers {

  "JsonMapper decimals" should "write exact decimals as strings of digits with their scale" in {
    val prices = Prices()
    prices.amount.set(BigDecimal("120.00"))
    prices.exact.set(new java.math.BigDecimal("12345678901234567890.123456789"))

    val json = JsonMapperSpec.mapper.serialize(prices, JsonMapperSpec.pricesMeta)

    json.selectDynamic("amount").asInstanceOf[String] shouldBe "120.00"
    json.selectDynamic("exact").asInstanceOf[String] shouldBe "12345678901234567890.123456789"
  }

  it should "read decimals from strings without losing digits and from JSON numbers" in {
    val fromStrings = JsonMapperSpec.mapper.deserialize[Prices](
      literal(amount = "19.90", exact = " 12345678901234567890.123456789 "),
      JsonMapperSpec.pricesMeta
    )
    fromStrings.amount.get shouldBe BigDecimal("19.90")
    fromStrings.amount.get.scale shouldBe 2
    fromStrings.exact.get shouldBe new java.math.BigDecimal("12345678901234567890.123456789")

    val fromNumbers = JsonMapperSpec.mapper.deserialize[Prices](
      literal(amount = 0.1, exact = 1000),
      JsonMapperSpec.pricesMeta
    )
    fromNumbers.amount.get shouldBe BigDecimal("0.1")
    fromNumbers.exact.get.compareTo(new java.math.BigDecimal("1000")) shouldBe 0
  }

  it should "reject values that are no decimal" in {
    an[IllegalArgumentException] should be thrownBy JsonMapperSpec.mapper.deserialize[Prices](
      literal(amount = true),
      JsonMapperSpec.pricesMeta
    )
  }
}


