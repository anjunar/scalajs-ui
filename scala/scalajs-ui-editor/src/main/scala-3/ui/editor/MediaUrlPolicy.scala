package ui.editor

import org.scalajs.dom
import scala.concurrent.Future

/** Synchronous in both SSR and the browser. Implementations may restrict media routes. */
trait MediaUrlPolicy {
  def resolve(src: String): Option[MediaReference]
}

object MediaUrlPolicy {
  val internal: MediaUrlPolicy = new MediaUrlPolicy {
    def resolve(src: String): Option[MediaReference] =
      Option.when(InternalImageUrl.valid(src))(MediaReference(src))
  }

  private[editor] def checked(policy: MediaUrlPolicy, src: String): Option[MediaReference] =
    if (!InternalImageUrl.valid(src)) None
    else
      policy
        .resolve(src)
        .filter(value => InternalImageUrl.valid(value.src) && value.mediaId.forall(_.trim.nonEmpty))

  private[editor] def image(policy: MediaUrlPolicy, value: ImageReference): Option[ImageReference] =
    checked(policy, value.src)
      .filter(ref => ref.mediaId.isEmpty || value.mediaId.isEmpty || ref.mediaId == value.mediaId)
      .map(ref => value.copy(src = ref.src, mediaId = ref.mediaId.orElse(value.mediaId)).validated)
}
