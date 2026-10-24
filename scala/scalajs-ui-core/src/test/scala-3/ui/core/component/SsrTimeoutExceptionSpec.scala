package ui.core.component

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

/** The SSR servers read `error.status === 504` on the rejected promise value, which is this very
  * object: `Future.toJSPromise` rejects with the Throwable itself. Without the exports the field is
  * name-mangled, the 504 branch is dead, and a timeout answers as a generic 500.
  */
class SsrTimeoutExceptionSpec extends AnyFlatSpec with Matchers {

  "SsrTimeoutException" should "expose its status and timeout to JavaScript callers" in {
    val error   = new SsrTimeoutException(250)
    val dynamic = error.asInstanceOf[js.Dynamic]

    dynamic.status.asInstanceOf[Int] shouldBe 504
    dynamic.timeoutMs.asInstanceOf[Int] shouldBe 250
    error.status shouldBe 504
    error.getMessage shouldBe "SSR render timed out after 250 ms"
  }
}
