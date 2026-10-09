import { attr, button, classes, div, listProperty, onClick, property, style, text } from "@anjunar/scalajs-ui-core";
import { carousel } from "@anjunar/scalajs-ui-controls";
import { translated } from "../../app/i18n.js";

interface Slide {
  readonly kicker: string;
  readonly title: string;
  readonly copy: string;
}

const slideCatalog: readonly Slide[] = [
  { kicker: "Atlas", title: "Architecture that keeps moving", copy: "The carousel owns the visible sequence while the slide renderer stays declarative." },
  { kicker: "Signal", title: "Auto-advance without hidden magic", copy: "A lifecycle-bound timer rotates through the same explicit slide collection." },
  { kicker: "Northwind", title: "SSR can surface every state", copy: "Stable dynamic ranges keep the server and hydration structure aligned." },
  { kicker: "Harbor", title: "Wrap-around is part of the contract", copy: "The step after the right edge returns to the beginning." },
  { kicker: "Keystone", title: "Every view has its place", copy: "The stage keeps nearby slides in sight without changing the active item." },
  { kicker: "Pulse", title: "A responsive rhythm", copy: "Choose how many neighboring slides remain visible on either side." },
  { kicker: "Orbit", title: "A complete loop", copy: "Seven slides make all three preview levels visible at once." },
];

export function controlsCarouselPage(): void {
  const slides = listProperty<Slide>([...slideCatalog]);
  const autoAdvanceMs = property(2600);
  const sidePreviewCount = property(2);

  div(() => {
    classes("flex", "flex-col", "gap-4");

    div(() => {
      classes("carousel-showcase-frame");
      carousel(
        slides,
        (slide, index) => {
          div(() => {
            classes("carousel-demo-slide");
            style("min-height", "320px");
            style("padding", "28px");
            div(() => {
              classes("carousel-demo-slide__kicker");
              text(slide.kicker);
            });
            div(() => {
              classes("carousel-demo-slide__title");
              text(`${index + 1}. ${slide.title}`);
            });
            div(() => {
              classes("carousel-demo-slide__copy");
              text(slide.copy);
            });
            div(() => {
              classes("carousel-demo-slide__footer");
              div(() => {
                classes("carousel-demo-slide__pill");
                text(translated("State"));
              });
              div(() => {
                classes("carousel-demo-slide__accent");
                text(translated("Looping sequence"));
              });
            });
          });
        },
        { autoAdvanceMs, sidePreviewCount, ssrShowAllStates: true }
      );
    });

    div(() => {
      classes("showcase-action-row", "carousel-preview-controls");
      div(() => {
        classes("carousel-preview-controls__label");
        text(translated("Previews per side"));
      });
      for (const count of [1, 2, 3]) {
        button(String(count), {}, () => {
          classes("carousel-preview-choice");
          attr("aria-pressed", sidePreviewCount.map((value) => String(value === count)));
          onClick(() => sidePreviewCount.set(count));
        });
      }
    });

    div(() => {
      classes("showcase-action-row");
      button(translated("Fast autoplay"), {}, () => onClick(() => autoAdvanceMs.set(1400)));
      button(translated("Slow autoplay"), {}, () => onClick(() => autoAdvanceMs.set(3400)));
      button(translated("Stop timer"), {}, () => onClick(() => autoAdvanceMs.set(0)));
    });

    div(() => {
      classes("showcase-result");
      div(() => text(autoAdvanceMs.map((milliseconds) => `autoAdvanceMs = ${milliseconds}`)));
    });
  });
}
