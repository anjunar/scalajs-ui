package ui.router

import ui.core.component.AbstractComponent
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class RouteMatcherSpec extends AnyFlatSpec with Matchers {

  private val empty: RouteContext => Future[AbstractComponent] =
    _ => Future.successful(Route.component {})

  "RouteMatcher" should "match a literal segment against its percent-encoded request" in {
    val routes = Seq(Route.view("/café")(empty))

    RouteMatcher.resolve(routes, "/caf%C3%A9").map(_.fullPath) shouldBe List("/café")
    RouteMatcher.resolve(routes, "/café").map(_.fullPath) shouldBe List("/café")
    RouteMatcher.resolve(routes, "/cafe") shouldBe Nil
  }

  it should "match an index child on the parent's own path" in {
    val index  = Route.view("")(empty)
    val detail = Route.view("posts/:slug")(empty)
    val routes = Seq(Route.view("/teams/:team", children = Seq(index, detail))(empty))

    val matches = RouteMatcher.resolve(routes, "/teams/core")

    matches.map(_.route) shouldBe List(routes.head, index)
    matches.map(_.fullPath) shouldBe List("/teams/:team", "/teams/:team")
    matches.last.params shouldBe Map("team" -> "core")

    RouteMatcher.resolve(routes, "/teams/core/posts/hello").map(_.route) shouldBe
      List(routes.head, detail)
  }

  it should "leave a parent without an index child as the leaf" in {
    val routes =
      Seq(Route.view("/teams/:team", children = Seq(Route.view("posts/:slug")(empty)))(empty))

    RouteMatcher.resolve(routes, "/teams/core").map(_.fullPath) shouldBe List("/teams/:team")
  }
}
