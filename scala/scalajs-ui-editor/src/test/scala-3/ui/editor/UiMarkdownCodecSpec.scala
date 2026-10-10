package ui.editor

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.image.*
import ember.editor.link.*
import ember.editor.list.ListExtension
import ember.editor.code.CodeExtension
import ember.editor.table.{TableExtension, TableNode}
import ember.editor.standard.{MarkdownSupports, TableSupport}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ui.core.i18n.{I18n, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog}
import ui.core.state.Property
import ember.editor.image.MediaUrlPolicy
import ui.editor.MediaReference
import ui.editor.{MediaUrlPolicy as EditorMediaUrlPolicy}

final class UiMarkdownCodecSpec extends AnyFlatSpec with Matchers {
  private val generator    = NodeIdGenerator.sequential("codec")
  private val nativePolicy = MediaUrlPolicy(schemes = Set.empty)
  private val extensions   = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        ImageExtension(generator, nativePolicy),
        LinkExtension(generator),
        ListExtension(generator),
        CodeExtension(generator),
        TableExtension(generator)
      )
    )
    .toOption
    .get
  private val codec = new UiMarkdownCodec(
    MarkdownSupports.everything(media = nativePolicy) ++ TableSupport.markdownRules,
    generator,
    EditorMediaUrlPolicy.internal
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
    val policy = new EditorMediaUrlPolicy {
      def resolve(src: String) =
        Some(MediaReference(if (src == "/alias") "/media/cat.webp" else src))
    }
    val canonical = new UiMarkdownCodec(
      MarkdownSupports.everything(media = nativePolicy) ++ TableSupport.markdownRules,
      generator,
      policy
    )
    val document =
      canonical.decode("![Real](/alias){width=320}", extensions.schema, NodeId("root")).toOption.get
    canonical.encode(document).toOption.get should include("![Real](/media/cat.webp){width=320}")
  }
  it should "reject invalid widths and disallowed media instead of silently dropping content" in {
    decode("![Image](/media/cat.webp){width=-1}").isLeft shouldBe true
    decode("![Image](https://external.test/cat.webp)").isLeft shouldBe true
  }
  it should "resolve Markdown errors through the owning locale with English fallback" in {
    val locale  = Property(I18nLocale.En)
    val runtime = I18nRuntime(
      locale,
      I18nResolver(
        MessageCatalog(
          I18n
            .entry(EditorMessages.unsupportedMarkdown.key)
            .translations(I18nLocale("de") -> "Bitte im Markdown-Quelltext bearbeiten."),
          I18n
            .entry(EditorMessages.invalidImageReference.key)
            .translations(I18nLocale("de") -> "Bildadresse oder Breite ungültig.")
        )
      )
    )
    val localized = new UiMarkdownCodec(
      MarkdownSupports.everything(media = nativePolicy) ++ TableSupport.markdownRules,
      generator,
      EditorMediaUrlPolicy.internal,
      new EditorText(runtime)
    )
    def error(source: String): String =
      localized.decode(source, extensions.schema, NodeId("localized")).swap.toOption.get.message
    error("<div>Text</div>") should include("HTML and additional text marks")
    error("![Image](https://external.test/image.png)") should include("image address or width")
    locale.set(I18nLocale("de"))
    error("<div>Text</div>") should include("Bitte im Markdown-Quelltext bearbeiten.")
    error("![Image](https://external.test/image.png)") should include(
      "Bildadresse oder Breite ungültig."
    )
  }

  it should "decode and re-encode GFM pipe tables through the native table model" in {
    val document = decode("| First | Second |\n| --- | --- |\n| A | B |").toOption.get
    document.inDocumentOrder.collect { case table: TableNode =>
      table.header
    }.toVector shouldBe Vector(
      true
    )
    val written = codec.encode(document).toOption.get
    written should include("First")
    written should include("Second")
    written should include("---")
  }
}
