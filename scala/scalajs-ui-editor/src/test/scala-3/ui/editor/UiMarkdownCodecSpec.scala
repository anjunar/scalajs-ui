package ui.editor

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.image.*
import ember.editor.link.*
import ember.editor.list.ListExtension
import ember.editor.code.CodeExtension
import ember.editor.standard.MarkdownSupports
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class UiMarkdownCodecSpec extends AnyFlatSpec with Matchers {
  private val generator    = NodeIdGenerator.sequential("codec")
  private val nativePolicy = ember.editor.image.MediaUrlPolicy(schemes = Set.empty)
  private val extensions   = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        ImageExtension(generator, nativePolicy),
        LinkExtension(generator),
        ListExtension(generator),
        CodeExtension(generator)
      )
    )
    .toOption
    .get
  private val codec = new UiMarkdownCodec(
    MarkdownSupports.everything(media = nativePolicy),
    generator,
    ui.editor.MediaUrlPolicy.internal
  )
  private def decode(source: String) = codec.decode(source, extensions.schema, NodeId("root"))

  "UI Markdown" should "preserve separate widths and titles for repeated image URLs" in {
    val source =
      "![One](/media/cat.webp \"First\"){width=680}\n\n![Two](/media/cat.webp){width=320}"
    val document = decode(source).toOption.get
    val images   = document.inDocumentOrder.collect { case image: ImageNode => image }.toVector
    images.map(_.width.map(_.value)) shouldBe Vector(Some(680), Some(320))
    val written = codec.encode(document).toOption.get
    written should include("{width=680}")
    written should include("{width=320}")
    written should include("\"First\"")
  }
  it should "leave image examples in code alone" in {
    val source =
      "```markdown\n![Example](/media/cat.webp){width=900}\n```\n\n![Real](/media/cat.webp){width=320}"
    val document = decode(source).toOption.get
    document.inDocumentOrder.collect { case image: ImageNode =>
      image.width.map(_.value)
    }.toVector shouldBe Vector(Some(320))
    codec.encode(document).toOption.get should include("{width=900}")
  }
  it should "leave escaped images alone when the real image uses the same URL" in {
    val document = decode(
      "\\![Example](/media/cat.webp){width=900}\n\n![Real](/media/cat.webp){width=320}"
    ).toOption.get
    document.inDocumentOrder.collect { case image: ImageNode =>
      image.width.map(_.value)
    }.toVector shouldBe Vector(Some(320))
    codec.encode(document).toOption.get should include("{width=900}")
  }
  it should "apply canonical media URLs before assigning occurrence widths" in {
    val policy = new ui.editor.MediaUrlPolicy {
      def resolve(src: String) =
        Some(ui.editor.MediaReference(if (src == "/alias") "/media/cat.webp" else src))
    }
    val canonical =
      new UiMarkdownCodec(MarkdownSupports.everything(media = nativePolicy), generator, policy)
    val document =
      canonical.decode("![Real](/alias){width=320}", extensions.schema, NodeId("root")).toOption.get
    canonical.encode(document).toOption.get should include("![Real](/media/cat.webp){width=320}")
  }
  it should "reject invalid widths and disallowed media instead of silently dropping content" in {
    decode("![Image](/media/cat.webp){width=-1}").isLeft shouldBe true
    decode("![Image](https://external.test/cat.webp)").isLeft shouldBe true
  }
  it should "report tables as source-only before a visual edit can change their meaning" in {
    decode("| First | Second |\n| --- | --- |\n| A | B |").isLeft shouldBe true
  }
}
