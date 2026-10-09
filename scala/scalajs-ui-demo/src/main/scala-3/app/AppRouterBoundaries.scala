package app

import ui.router.RouteFailure

/** How the demo answers a request that cannot be served.
  *
  * The pages themselves are routes -- `/404` and `/500` in [[AppRoutes]]. What is left here is the
  * mapping from a failure to one of them. Pending loaders retain the current page.
  */
object AppRouterBoundaries {

  val onFailure: RouteFailure => Option[String] = {
    case _: RouteFailure.NotMatched => Some("/404")
    case _: RouteFailure.LoadFailed => Some("/500")
  }

}
