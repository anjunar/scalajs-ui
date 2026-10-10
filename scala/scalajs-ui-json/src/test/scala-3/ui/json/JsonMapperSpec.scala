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

class JsonMapperSpec extends AnyFlatSpec with Matchers {

  "JsonMapper annotations" should "serialize and deserialize JsonProperty field names" in {
    val mapper = JsonMapper()
    val person = AnnotatedPerson()
    person.name.set("Ada")
    person.age.set(37)

    val json = mapper.serialize(person, JsonMapperSpec.annotatedPersonMeta)

    json.selectDynamic("fullName").asInstanceOf[String] shouldBe "Ada"
    js.isUndefined(json.selectDynamic("name")) shouldBe true
    json.selectDynamic("age").asInstanceOf[Double].toInt shouldBe 37

    val restored = mapper.deserialize[AnnotatedPerson](
      literal(fullName = "Grace", age = 41),
      JsonMapperSpec.annotatedPersonMeta
    )

    restored.name.get shouldBe "Grace"
    restored.age.get shouldBe 41
  }

  it should "provide the inline companion API from a local JsonSchema" in {
    given JsonSchema[AnnotatedPerson] = JsonMapperSpec.annotatedPersonSchema

    val person = AnnotatedPerson()
    person.name.set("Inline")
    person.age.set(21)

    val json     = JsonMapper.serialize(person)
    val restored = JsonMapper.deserialize[AnnotatedPerson](json)

    restored.name.get shouldBe "Inline"
    restored.age.get shouldBe 21
  }

  it should "resolve nested models from local schema dependencies" in {
    given JsonSchema[Profile] =
      JsonMapperSpec.profileSchema.withDependencies(JsonMapperSpec.nestedInfoSchema)

    val profile = Profile()
    profile.id.set("profile-1")
    profile.info.get.id.set("info-1")
    profile.info.get.firstName.set("Nested")

    val json     = JsonMapper.serialize(profile)
    val restored = JsonMapper.deserialize[Profile](json)

    restored.id.get shouldBe "profile-1"
    restored.info.get.id.get shouldBe "info-1"
    restored.info.get.firstName.get shouldBe "Nested"
  }

  it should "derive nested models without an explicit schema" in {
    val restored = JsonMapper.deserialize[Profile](
      literal(id = "profile-2", info = literal(id = "info-2", firstName = "Derived"))
    )

    restored.id.get shouldBe "profile-2"
    restored.info.get.id.get shouldBe "info-2"
    restored.info.get.firstName.get shouldBe "Derived"
  }

  it should "read foreign type metadata into a concrete model" in {
    val restored = JsonMapper.deserialize[AnnotatedPerson](
      literal(`@type` = "foreign-person", fullName = "Grace", age = 41)
    )

    restored.name.get shouldBe "Grace"
    restored.age.get shouldBe 41
  }

  it should "ignore JsonIgnore properties in both directions by default" in {
    val mapper = JsonMapperSpec.mapper
    val user   = IgnoredSecret()
    user.visible.set("public")
    user.secret.set("private")

    val json = mapper.serialize(user, JsonMapperSpec.ignoredSecretMeta)

    json.selectDynamic("visible").asInstanceOf[String] shouldBe "public"
    js.isUndefined(json.selectDynamic("secret")) shouldBe true

    val restored = mapper.deserialize[IgnoredSecret](
      literal(visible = "client", secret = "tampered"),
      JsonMapperSpec.ignoredSecretMeta
    )

    restored.visible.get shouldBe "client"
    restored.secret.get shouldBe ""
  }

  it should "support directional JsonIgnore properties" in {
    val mapper = JsonMapperSpec.mapper
    val model  = DirectionalIgnore()
    model.readOnly.set("server-value")
    model.writeOnly.set("server-secret")

    val json = mapper.serialize(model, JsonMapperSpec.directionalIgnoreMeta)

    json.selectDynamic("readOnly").asInstanceOf[String] shouldBe "server-value"
    js.isUndefined(json.selectDynamic("writeOnly")) shouldBe true

    val restored = mapper.deserialize[DirectionalIgnore](
      literal(readOnly = "client-value", writeOnly = "client-secret"),
      JsonMapperSpec.directionalIgnoreMeta
    )

    restored.readOnly.get shouldBe ""
    restored.writeOnly.get shouldBe "client-secret"
  }

  "JsonMapper polymorphism" should "use JsonType values in both directions" in {
    val mapper = JsonMapperSpec.mapper
    val circle = Circle()
    circle.radius.set(12)

    val json = mapper.serialize(circle, JsonMapperSpec.shapeMeta)

    json.selectDynamic("@type").asInstanceOf[String] shouldBe "circle"
    json.selectDynamic("radius").asInstanceOf[Double].toInt shouldBe 12

    val restored = mapper.deserialize[Shape](
      literal(`@type` = "circle", radius = 9),
      JsonMapperSpec.shapeMeta
    )

    restored shouldBe a[Circle]
    restored.asInstanceOf[Circle].radius.get shouldBe 9
  }

  it should "derive annotated subtypes without an explicit schema" in {
    val restored = JsonMapper.deserialize[Shape](literal(`@type` = "circle", radius = 7))

    restored shouldBe a[Circle]
    restored.asInstanceOf[Circle].radius.get shouldBe 7

    val dot = JsonMapper.deserialize[Shape](literal(`@type` = "dot"))
    dot shouldBe a[Dot]
    Reflect
      .lookupInstantiatableClass("ui.json.Dot")
      .flatMap(_.getConstructor())
      .nonEmpty shouldBe true
  }

  it should "accept a model class from JsonProperty" in {
    val restored = JsonMapper.deserialize[ShapeHolder](
      literal(shape = literal(`@type` = "plainCircle", radius = 5))
    )

    restored.shape shouldBe a[PlainCircle]
    restored.shape.asInstanceOf[PlainCircle].radius.get shouldBe 5
  }

  it should "reject unknown and missing types for abstract models" in {
    val mapper = JsonMapperSpec.mapper

    val missing = intercept[IllegalArgumentException] {
      mapper.deserialize[Shape](literal(radius = 9), JsonMapperSpec.shapeMeta)
    }
    missing.getMessage should include("Missing @type")

    val unknown = intercept[IllegalArgumentException] {
      mapper.deserialize[Shape](
        literal(`@type` = "triangle", radius = 9),
        JsonMapperSpec.shapeMeta
      )
    }
    unknown.getMessage should include("Unknown @type 'triangle'")
  }

  "JsonMapper state values" should "serialize mutated ListProperty contents" in {
    val mapper = JsonMapperSpec.mapper
    val thread = CommentThread()
    val reply  = Reply()
    reply.text.set("Hallo")
    thread.replies.addOne(reply)

    val json    = mapper.serialize(thread, JsonMapperSpec.commentThreadMeta)
    val replies = json.selectDynamic("replies").asInstanceOf[js.Array[js.Dynamic]]

    replies.length shouldBe 1
    replies(0).selectDynamic("text").asInstanceOf[String] shouldBe "Hallo"
  }

  it should "include only dirty nested payload plus JsonId fields" in {
    val mapper  = JsonMapperSpec.mapper
    val profile = Profile()
    profile.id.set("user-1")
    profile.info.get.id.set("info-1")
    profile.info.get.firstName.set("Patrick1")
    profile.info.get.firstName.setDefault("Patrick1")
    profile.info.get.lastName.set("Tester")
    profile.info.get.lastName.setDefault("Tester")
    profile.info.get.firstName.set("Patrick")

    val json = mapper.serialize(profile, JsonMapperSpec.profileMeta)
    val info = json.selectDynamic("info").asInstanceOf[js.Dynamic]

    json.selectDynamic("id").asInstanceOf[String] shouldBe "user-1"
    info.selectDynamic("id").asInstanceOf[String] shouldBe "info-1"
    info.selectDynamic("firstName").asInstanceOf[String] shouldBe "Patrick"
    js.isUndefined(info.selectDynamic("lastName")) shouldBe true
  }

  it should "reset Property and ListProperty defaults after deserialization" in {
    val mapper   = JsonMapperSpec.mapper
    val restored = mapper.deserialize[CommentThread](
      literal(replies = js.Array(literal(text = "stable"))),
      JsonMapperSpec.commentThreadMeta
    )

    restored.replies.isDirty shouldBe false
    restored.replies.head.text.get shouldBe "stable"
    restored.replies.head.text.isDirty shouldBe false
  }

  "JsonMapper value types" should "roundtrip options, maps, collections, UUIDs, and raw JSON" in {
    val mapper = JsonMapperSpec.mapper
    val id     = util.UUID.fromString("92707f9f-a861-4d45-9d4b-47832fe06741")
    val model  = ValueTypes()
    model.optional.set(Some("present"))
    model.identifiers.set(ListMap("primary" -> id))
    model.numbers.set(List(1, 2, 3))
    model.array.set(Array(4, 5, 6))
    model.jsArray.set(js.Array(7, 8, 9))
    model.flags.set(Set(true, false))
    model.longNumber.set(9007199254740991L)
    model.floatNumber.set(1.25f)
    model.shortNumber.set(12.toShort)
    model.byteNumber.set(3.toByte)
    model.character.set('J')
    model.raw.set(literal(nested = "untouched"))
    val reply = Reply()
    reply.text.set("array item")
    model.replies.set(Array(reply))

    val json     = mapper.serialize(model, JsonMapperSpec.valueTypesMeta)
    val restored = mapper.deserialize[ValueTypes](json, JsonMapperSpec.valueTypesMeta)

    restored.optional.get shouldBe Some("present")
    restored.identifiers.get shouldBe ListMap("primary" -> id)
    restored.numbers.get shouldBe List(1, 2, 3)
    restored.array.get.toSeq shouldBe Seq(4, 5, 6)
    restored.jsArray.get.toSeq shouldBe Seq(7, 8, 9)
    restored.flags.get shouldBe Set(true, false)
    restored.longNumber.get shouldBe 9007199254740991L
    restored.floatNumber.get shouldBe 1.25f
    restored.shortNumber.get shouldBe 12.toShort
    restored.byteNumber.get shouldBe 3.toByte
    restored.character.get shouldBe 'J'
    restored.replies.get.map(_.text.get).toSeq shouldBe Seq("array item")
    restored.raw.get
      .asInstanceOf[js.Dynamic]
      .selectDynamic("nested")
      .asInstanceOf[String] shouldBe "untouched"
  }

  it should "map a single map property as an inline JSON object" in {
    val mapper = JsonMapperSpec.mapper
    val labels = Labels(Map("de" -> "Hallo", "en" -> "Hello"))

    val json = mapper.serialize(labels, JsonMapperSpec.labelsMeta)

    json.selectDynamic("de").asInstanceOf[String] shouldBe "Hallo"
    js.isUndefined(json.selectDynamic("values")) shouldBe true

    val restored = mapper.deserialize[Labels](
      literal(de = "Guten Tag", en = "Hello"),
      JsonMapperSpec.labelsMeta
    )
    restored.values shouldBe Map("de" -> "Guten Tag", "en" -> "Hello")
  }

  it should "deserialize arrays and treat null arrays as empty" in {
    val mapper = JsonMapperSpec.mapper
    val json   = js.Array[js.Dynamic](
      literal(fullName = "Ada", age = 37),
      literal(fullName = "Grace", age = 41)
    )

    val restored = mapper.deserializeArray[AnnotatedPerson](
      json,
      JsonMapperSpec.annotatedPersonMeta
    )

    restored.map(_.name.get) shouldBe Seq("Ada", "Grace")
    mapper
      .deserializeArray[AnnotatedPerson](null, JsonMapperSpec.annotatedPersonMeta) shouldBe empty
  }

  it should "resolve generic property types from parameterized metadata" in {
    val mapper = JsonMapperSpec.mapper
    val model  = GenericBox[String]()
    model.value.set("typed")

    val json     = mapper.serialize(model, JsonMapperSpec.stringBoxMeta)
    val restored = mapper.deserialize[GenericBox[String]](
      json,
      JsonMapperSpec.stringBoxMeta
    )

    restored.value.get shouldBe "typed"
    restored.value.isDirty shouldBe false
  }
}

object JsonMapperSpec {
  val annotatedPersonSchema   = JsonSchema(() => AnnotatedPerson())
  val ignoredSecretSchema     = JsonSchema(() => IgnoredSecret())
  val directionalIgnoreSchema = JsonSchema(() => DirectionalIgnore())
  val circleSchema            = JsonSchema(() => Circle())
  val shapeSchema             = JsonSchema.abstractType[Shape](circleSchema)
  val replySchema             = JsonSchema(() => Reply())
  val commentThreadSchema     = JsonSchema(() => CommentThread())
  val nestedInfoSchema        = JsonSchema(() => NestedInfo())
  val profileSchema           = JsonSchema(() => Profile())
  val valueTypesSchema        = JsonSchema(() => ValueTypes())
  val pricesSchema            = JsonSchema(() => Prices())
  val labelsSchema            = JsonSchema(() => Labels())
  val genericBoxSchema        = JsonSchema(() => GenericBox[String]())

  val annotatedPersonMeta   = annotatedPersonSchema.descriptor
  val ignoredSecretMeta     = ignoredSecretSchema.descriptor
  val directionalIgnoreMeta = directionalIgnoreSchema.descriptor
  val shapeMeta             = shapeSchema.descriptor
  val commentThreadMeta     = commentThreadSchema.descriptor
  val profileMeta           = profileSchema.descriptor
  val valueTypesMeta        = valueTypesSchema.descriptor
  val pricesMeta            = pricesSchema.descriptor
  val labelsMeta            = labelsSchema.descriptor
  val stringBoxMeta         = ReflectMacros.reflectType[GenericBox[String]]

  private val schemas = Seq(
    annotatedPersonSchema,
    ignoredSecretSchema,
    directionalIgnoreSchema,
    shapeSchema,
    replySchema,
    commentThreadSchema,
    nestedInfoSchema,
    profileSchema,
    valueTypesSchema,
    pricesSchema,
    labelsSchema,
    genericBoxSchema
  )

  def mapper: JsonMapper =
    JsonMapper(schemas*)
}
