package ui.bridge

import ui.core.component.AbstractComponent
import ui.core.layout.Drawer
import ui.core.render.Cursor

import scala.scalajs.js
private[bridge] object DrawerFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    Drawer.drawer {
      val self = summon[Drawer]

      options.get("open").foreach(value => self.openProperty.set(ControlFactories.bool(value)))
      options
        .get("drawerWidth")
        .foreach(value => self.drawerWidthProperty.set(ControlFactories.str(value)))
      options
        .get("closeOnScrimClick")
        .foreach(value => self.closeOnScrimClickProperty.set(ControlFactories.bool(value)))
      options.get("side").foreach { value =>
        self.sideProperty.set(
          if (ControlFactories.str(value) == "end") Drawer.Side.End else Drawer.Side.Start
        )
      }

      val isOpenFn: js.Function0[Boolean]        = () => self.openProperty.get
      val setOpenFn: js.Function1[Boolean, Unit] = value => self.openProperty.set(value)
      val toggleFn: js.Function0[Unit] = () => self.openProperty.set(!self.openProperty.get)

      val handle = js.Dynamic
        .literal(isOpen = isOpenFn, setOpen = setOpenFn, toggle = toggleFn)
        .asInstanceOf[DrawerHandleFacade]

      options("compose")
        .asInstanceOf[
          js.Function3[DrawerHandleFacade, ComponentHandleBridge, ScopeHandleBridge, Unit]
        ](
          handle,
          new ComponentHandleBridge(self),
          new ScopeHandleBridge(self, summon[Cursor])
        )
    }
}

