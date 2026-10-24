package ui.bridge

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.concurrent.Future

/** Exercises the i18n facade end to end through `ScopeHandleBridge`, the same way a TypeScript
  * consumer's `i18nProvider()`/`t()`/`i18n\`...\`` would -- but built here as the raw JS shapes
  * `npm/scalajs-ui-core/src/i18n.ts` produces, since this suite runs in plain Node without the npm
  * package. `renderToString` only, for the same reason `UiRuntimeBridgeSpec` gives: `mount`/
  * `hydrate` need a real `dom.Element` this test environment cannot provide.
  */
class I18nFactoriesSpec extends AsyncFlatSpec with Matchers {

  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  // bridgeRuntime no longer forces registration itself (BridgeRuntime.scala) -- a real npm
  // consumer's index.js composes installXRuntime() calls explicitly, so this suite does the same.
  CoreRuntime.install()

  private val runtime = BridgeRuntime.bridgeRuntime

  private def render(
      build: js.Function1[ScopeHandleBridge, Unit]
  ): Future[SsrResultHandle] =
    runtime.renderToString(build, js.undefined).toFuture

  /** The `RuntimeMessageFacade` shape `i18n.ts`'s `` i18n`Hello ${named("name", "Mira")}` ``
    * produces. The fingerprint is the same fixture `I18nSpec.scala` locks down for "Hello {name}"
    * -- reused here, not re-derived, since this suite is about the JS -> Scala crossing, not the
    * fingerprint algorithm itself.
    */
  private def jsMessage(name: String, value: String): js.Dictionary[js.Any] =
    js.Dictionary(
      "key" -> js.Dictionary(
        "source"       -> "Hello {name}",
        "fingerprint"  -> "51e962fae94c4a20",
        "placeholders" -> js.Array("name")
      ),
      "args" -> js.Array(js.Dictionary("name" -> "name", "value" -> value))
    )

  private def jsCatalogEntry(translations: (String, String)*): js.Dictionary[js.Any] =
    js.Dictionary(
      "key" -> js.Dictionary(
        "source"       -> "Hello {name}",
        "fingerprint"  -> "51e962fae94c4a20",
        "placeholders" -> js.Array("name")
      ),
      "translations" -> js.Dictionary(translations*)
    )

  private def jsProviderOptions(
      catalog: js.Array[js.Any] = js.Array(),
      supportedLocales: js.Array[String] = js.Array("en", "de"),
      defaultLocale: String = "en",
      initialUrl: js.UndefOr[String] = js.undefined
  ): js.Dictionary[js.Any] =
    js.Dictionary(
      "catalog"          -> catalog,
      "supportedLocales" -> supportedLocales,
      "defaultLocale"    -> defaultLocale,
      "initialUrl"       -> initialUrl
    )

  "the i18n facade" should "fall back to the message's own source when the catalog has no match" in {
    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component(
        "i18n-provider",
        jsProviderOptions(),
        (_, inner) => {
          inner.text(inner.i18nText(jsMessage("name", "Mira").asInstanceOf[RuntimeMessageFacade]));
          ()
        }
      )
      ()
    }

    render(build).map(_.html should include("Hello Mira"))
  }

  it should "resolve a catalog translation for the locale implied by initialUrl" in {
    val catalog = js.Array[js.Any](jsCatalogEntry("de" -> "Hallo {name}"))

    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component(
        "i18n-provider",
        jsProviderOptions(catalog = catalog, initialUrl = "/de/dashboard"),
        (_, inner) => {
          inner.text(inner.i18nText(jsMessage("name", "Mira").asInstanceOf[RuntimeMessageFacade]));
          ()
        }
      )
      ()
    }

    render(build).map(_.html should include("Hallo Mira"))
  }

  it should "match a contextual (i18nc) message against its catalog entry" in {
    // `i18n.ts` crosses `context` as `contract.ts`'s `MessageContext` -- an object `{ value }`, not
    // the bare string. `MessageCatalog.entryFor` compares contexts by equality, so the crossing has
    // to read the string inside; wrapping the object itself left two `{ value: "fruit" }` unequal
    // and no `i18nc` message ever translated. The fingerprint is a fixture both sides share: the
    // lookup is by fingerprint, then context, and the algorithm is `i18n.test.ts`'s business.
    def contextualKey(): js.Dictionary[js.Any] =
      js.Dictionary(
        "source"       -> "date",
        "context"      -> js.Dictionary("value" -> "fruit"),
        "fingerprint"  -> "ctx-date-fruit",
        "placeholders" -> js.Array[String]()
      )
    val message = js.Dictionary[js.Any]("key" -> contextualKey(), "args" -> js.Array[js.Any]())
    val entry   = js.Dictionary[js.Any](
      "key"          -> contextualKey(),
      "translations" -> js.Dictionary("de" -> "Dattel")
    )

    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component(
        "i18n-provider",
        jsProviderOptions(catalog = js.Array[js.Any](entry), initialUrl = "/de/"),
        (_, inner) => {
          inner.text(inner.i18nText(message.asInstanceOf[RuntimeMessageFacade]))
          ()
        }
      )
      ()
    }

    render(build).map { result =>
      result.html should include("Dattel")
      result.html should not include "date"
    }
  }

  it should "expose the resolved locale and let i18nSetLocale change what i18nText resolves" in {
    val catalog          = js.Array[js.Any](jsCatalogEntry("de" -> "Hallo {name}"))
    var seenBeforeSwitch = ""
    var textAfterSwitch: ReadOnlyPropertyHandle[String] = null

    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component(
        "i18n-provider",
        jsProviderOptions(catalog = catalog),
        (_, inner) => {
          seenBeforeSwitch = inner.i18nLocale().get
          inner.i18nSetLocale("de")
          textAfterSwitch =
            inner.i18nText(jsMessage("name", "Mira").asInstanceOf[RuntimeMessageFacade])
          ()
        }
      )
      ()
    }

    render(build).map { _ =>
      seenBeforeSwitch shouldBe "en"
      textAfterSwitch.get shouldBe "Hallo Mira"
    }
  }

  it should "report the configured supported and default locale codes" in {
    var supported: js.Array[String] = js.Array()
    var fallback                    = ""

    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.component(
        "i18n-provider",
        jsProviderOptions(),
        (_, inner) => {
          supported = inner.i18nSupportedLocales()
          fallback = inner.i18nDefaultLocale()
          ()
        }
      )
      ()
    }

    render(build).map { _ =>
      supported.toSeq shouldBe Seq("en", "de")
      fallback shouldBe "en"
    }
  }

  it should "reject i18nText when no i18n-provider is an ancestor" in {
    val build: js.Function1[ScopeHandleBridge, Unit] = { scope =>
      scope.i18nText(jsMessage("name", "Mira").asInstanceOf[RuntimeMessageFacade])
      ()
    }

    recoverToSucceededIf[IllegalStateException](render(build))
  }
}
