package ui.control.virtualized

import ui.core.component.AbstractComponent
import ui.core.context.UrlScope
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.Anchor.{anchor, href}
import ui.core.layout.Condition.when
import ui.core.layout.Div
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.remote.RemoteListDataSource
import ui.core.render.DomHostElement
import ui.core.state.{CompositeDisposable, Disposable, ListDataSource, ListProperty, Property}
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.js

/** Shared base for TableView, DataGrid, and VirtualListView.
  *
  * Before P3-1, the three shared about 70 to 100 identically named members -- scroll state,
  * viewport measurement, remote integration, item state, revision counters, and DOM access were
  * implemented three times. Every fix had to be made three times; `requestLazyLoadIfNecessary` was
  * not (see scala/scalajs-ui-controls/VIRTUALIZATION.md).
  *
  * What actually distinguishes the three lives in [[ItemGeometry]].
  *
  * The subclass provides:
  *   - [[geometry]] -- where item i is located and what is visible
  *   - [[renderableCount]] -- how many items can be rendered in total
  *   - [[recomputeVisible]] -- rebuilding the control-specific visible list
  *   - [[handleLocalItemsChange]] and [[resetMeasurements]]
  */
enum CollectionDisplayMode {
  case Paging, Scrolling
}
