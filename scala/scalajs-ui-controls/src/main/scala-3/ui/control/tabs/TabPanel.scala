package ui.control.tabs

import ui.control.tabs.Tabs
import ui.control.tabs.Tabs.TabSpec
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.classIf
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

private final class TabPanel(
    tabs: Tabs,
    tab: TabSpec,
    index: Int,
    keepMounted: Boolean
) extends AbstractComponent {
  override val tagName: String = "div"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      addClass("ui-tabs__panel")
      setAttribute("role", "tabpanel")
      addDisposable(tabs.idPrefixProperty.observe {
        case Some(prefix) =>
          setAttribute("id", Tabs.panelId(prefix, index))
          setAttribute("aria-labelledby", Tabs.tabId(prefix, index))
        case None => ()
      })

      if (keepMounted) {
        val active = tabs.selectedIndexProperty.map(_ == index)
        classIf("ui-tabs__panel--active", active)
        addDisposable(active.observe { selected =>
          setAttribute("aria-hidden", (!selected).toString)
          // Remove rather than set "": an SSR host keeps an empty value as `display: `.
          if (selected) removeStyle("display") else setStyle("display", "none")
        })
      } else {
        addClass("ui-tabs__panel--active")
        setAttribute("aria-hidden", "false")
      }

      tab.render(using tabs)(using this)(using cursor)
    }
}
