package ui.editor

import ember.editor.core.*
import ember.editor.forms.{FieldCodec, FieldError}
import ember.editor.image.{ImageNode, PositivePixels, MediaId, MediaReference as NativeReference}
import ember.editor.markdown.*
import scala.collection.mutable

/** The UI's persisted Markdown dialect: CommonMark plus image {width=N}. Media IDs are resolved
  * from application URLs, never serialized as transport metadata.
  */
private[editor] final class UiMarkdownCodec(
    rules: MarkdownSupport,
    generator: NodeIdGenerator,
    policy: MediaUrlPolicy
) extends FieldCodec {
  val name                                               = "ui-markdown"
  def emptyValue(schema: Schema, rootId: NodeId): String = ""
  def decode(source: String, schema: Schema, rootId: NodeId): Either[EditorError, Document] = {
    // Tables are outside the native model. Keep their source editable without importing them
    // as paragraphs and changing their meaning on the next unrelated edit.
    var unsupported = false
    MarkdownImages.mapProse(source) { prose =>
      if (
        "(?im)^ *\\|? *:?-{3,}:? *\\|.*$|</?[a-z][^>]*>|\\+\\+[^+]+\\+\\+|~~[^~]+~~|==[^=]+==".r
          .findFirstIn(prose)
          .nonEmpty
      )
        unsupported = true
      prose
    }
    if (unsupported)
      return Left(
        FieldError.NotDecodable(
          name,
          "Tabellen, HTML und zusätzliche Textmarkierungen werden derzeit im Markdown-Quelltext bearbeitet."
        )
      )
    val widths     = mutable.Map.empty[String, mutable.Queue[Option[Int]]]
    var failure    = Option.empty[EditorError]
    val commonMark = MarkdownImages.images(source) { matched =>
      MarkdownImages.parse(matched.matched, policy) match {
        case Some(image) =>
          if (image.widthPx.exists(PositivePixels.parse(_).isEmpty))
            failure = Some(
              FieldError
                .NotDecodable(name, "Bildbreite liegt außerhalb des unterstützten Bereichs.")
            )
          widths.getOrElseUpdate(image.src, mutable.Queue.empty).enqueue(image.widthPx)
          MarkdownImages.format(image.copy(widthPx = None), policy)
        case None =>
          failure = Some(FieldError.NotDecodable(name, "Ungültige Bildadresse oder Bildbreite."))
          matched.matched
      }
    }
    failure match {
      case Some(error) => Left(error)
      case None        =>
        MarkdownCodec.decode(commonMark, schema, rules, generator, rootId).flatMap { decoded =>
          val losses = decoded.diagnostics.filter(_.loss)
          if (losses.nonEmpty)
            Left(FieldError.NotDecodable(name, losses.map(_.message).mkString("; ")))
          else {
            val nodes = decoded.document.inDocumentOrder.map {
              case image: ImageNode =>
                val width = widths
                  .get(image.src.value)
                  .filter(_.nonEmpty)
                  .flatMap(_.dequeue())
                  .flatMap(PositivePixels.parse)
                val reference = MediaUrlPolicy.checked(policy, image.src.value)
                if (reference.isEmpty)
                  failure = Some(FieldError.NotDecodable(name, "Bildadresse ist nicht zulässig."))
                image.copy(
                  width = width,
                  source =
                    image.source.copy(mediaId = reference.flatMap(_.mediaId).flatMap(MediaId.parse))
                )
              case node => node
            }.toVector
            failure.toLeft(Document.unsafe(schema, rootId, nodes))
          }
        }
    }
  }
  def encode(document: Document): Either[EditorError, String] = {
    val widths  = mutable.Map.empty[String, mutable.Queue[Option[Int]]]
    var failure = Option.empty[EditorError]
    val nodes   = document.inDocumentOrder.map {
      case image: ImageNode =>
        val reference = MediaUrlPolicy.checked(policy, image.src.value)
        if (
          reference.isEmpty || reference.exists(ref =>
            ref.src != image.src.value ||
              (ref.mediaId.nonEmpty && image.source.mediaId.nonEmpty && ref.mediaId != image.source.mediaId
                .map(_.value))
          )
        )
          failure =
            Some(FieldError.NotRepresentable(name, Vector("Bildadresse ist nicht zulässig.")))
        widths
          .getOrElseUpdate(image.src.value, mutable.Queue.empty)
          .enqueue(image.width.map(_.value))
        // Height has no public UI Markdown spelling and must still fail Strict encoding.
        image.copy(width = None, source = NativeReference(image.src))
      case node => node
    }.toVector
    failure match {
      case Some(error) => Left(error)
      case None        =>
        MarkdownCodec
          .encode(
            Document.unsafe(document.schema, document.rootId, nodes),
            rules,
            LossPolicy.Strict
          )
          .map { encoded =>
            MarkdownImages.images(encoded.source) { matched =>
              val width = MarkdownImages
                .parse(matched.matched, policy)
                .flatMap(image => widths.get(image.src).filter(_.nonEmpty).flatMap(_.dequeue()))
              matched.matched + width.map(n => s"{width=$n}").getOrElse("")
            }
          }
    }
  }
}
