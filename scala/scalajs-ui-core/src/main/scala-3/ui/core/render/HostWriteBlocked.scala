package ui.core.render

import ui.core.state.Disposable
import org.scalajs.dom
import scala.collection.mutable

final class HostWriteBlocked
    extends IllegalStateException("The host is protected from UI mutations.")
