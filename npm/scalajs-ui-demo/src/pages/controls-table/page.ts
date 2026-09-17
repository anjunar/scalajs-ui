import { attr, button, classes, classIf, div, element, on, onClick, onInput, property, style, text, when } from "@anjunar/scalajs-ui-core";
import { overlay } from "@anjunar/scalajs-ui-viewport";
import { column, columnGroup, remoteSource, tableView } from "@anjunar/scalajs-ui-controls";
import type { Property, ReadOnlyProperty, UiEvent } from "@anjunar/scalajs-ui-core";
import type { ColumnDef, ColumnResizePolicy, RemotePage, RemoteSource, SortSpec, TableCellContext, TableDirection, TableSelectionMode, TableViewHandle } from "@anjunar/scalajs-ui-controls";
import { translated } from "../../app/i18n.js";

const input = element("input");
const ul = element("ul");
const li = element("li");

interface Book {
  readonly title: string;
  readonly author: string;
  readonly year: number;
}

interface Query {
  readonly offset: number;
  readonly limit: number;
  readonly sorting: readonly SortSpec[];
}

const TOTAL_BOOKS = 1000;
const PAGE_SIZE = 50;
const titles = ["The Long Route", "Signal Garden", "Atlas Notes", "Quiet Systems", "Northwind"];
const authors = ["Ada Reed", "Mira Chen", "Noah Klein", "Lea Ortiz", "Sam Okafor"];

const books: readonly Book[] = Array.from({ length: TOTAL_BOOKS }, (_, index) => ({
  title: `${titles[index % titles.length]} ${index + 1}`,
  author: authors[(index * 3) % authors.length],
  year: 1980 + (index % 46),
}));

function slice(query: Query): readonly Book[] {
  const ordered = query.sorting.length === 0
    ? books
    : [...books].sort((left, right) => {
        // The demo's simulated remote loader, not a TableView-side sort.
        for (const term of query.sorting) {
          const leftValue = left[term.field as keyof Book];
          const rightValue = right[term.field as keyof Book];
          const result = leftValue < rightValue ? -1 : leftValue > rightValue ? 1 : 0;
          if (result !== 0) return term.ascending ? result : -result;
        }
        return 0;
      });
  return ordered.slice(query.offset, query.offset + query.limit);
}

function loadPage(query: Query): Promise<RemotePage<Book, Query>> {
  return Promise.resolve({
    items: slice(query),
    offset: query.offset,
    totalCount: TOTAL_BOOKS,
    hasMore: query.offset + query.limit < TOTAL_BOOKS,
  });
}

function inputValue(event: UiEvent): string {
  return (event.target as HTMLInputElement | null)?.value ?? "";
}

/** One labelled block of the control bar: its buttons plus the hints that explain them. */
function controlGroup(
  label: ReadOnlyProperty<string>,
  hints: readonly ReadOnlyProperty<string>[],
  actions: () => void,
): void {
  div(() => {
    classes("table-demo__group");
    div(() => {
      classes("table-demo__group-label");
      text(label);
    });
    div(() => {
      classes("table-demo__group-actions");
      actions();
    });
    if (hints.length > 0) {
      ul(() => {
        classes("table-demo__hints");
        for (const hint of hints) {
          li(() => {
            classes("table-demo__hint");
            text(hint);
          });
        }
      });
    }
  });
}

/** One readout tile. The value stays reactive; only the label is static. */
function stat(label: ReadOnlyProperty<string>, value: ReadOnlyProperty<string>): void {
  div(() => {
    classes("table-demo__stat");
    div(() => {
      classes("table-demo__stat-label");
      text(label);
    });
    div(() => {
      classes("table-demo__stat-value");
      text(value);
    });
  });
}

export function controlsTablePage(): void {
  const showAuthors = property(true);
  const selectionMode = property<TableSelectionMode>("multiple");
  const cellSelectionEnabled = property(false);
  const resizePolicy = property<ColumnResizePolicy>("flex-last-column");
  const direction = property<TableDirection>("ltr");
  const notes = new Map<string, Property<string>>();
  const noteFor = (book: Book): Property<string> => {
    let note = notes.get(book.title);
    if (note === undefined) {
      note = property("");
      notes.set(book.title, note);
    }
    return note;
  };
  let table!: TableViewHandle<Book>;
  const initialQuery: Query = { offset: 0, limit: PAGE_SIZE, sorting: [] };
  const source: RemoteSource<Book, Query> = remoteSource({
    load: loadPage,
    initialQuery,
    initial: slice(initialQuery),
    totalCount: TOTAL_BOOKS,
    rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }),
    sortQuery: (query, sorting) => ({ ...query, offset: 0, sorting }),
  });

  div(() => {
    classes("table-demo");

    div(() => {
      classes("showcase-note");
      div(() => {
        classes("showcase-note__title");
        text(translated("50 initial rows · 1,000 total"));
      });
      div(() => {
        classes("showcase-note__body");
        text(translated("Scroll through remote ranges or sort any column; the table keeps one stable virtual surface."));
      });
    });

    div(() => {
      classes("table-page__table", "table-demo__surface");
      style("height", "420px");
      style("min-height", "0");
      table = tableView(
        source,
        [
          columnGroup(translated("Book").get, [
            column(translated("Title").get, (book) => text(book.title), {
              prefWidth: 280, minWidth: 140, maxWidth: 900, sortable: true, sortKey: "title",
              // C09: replaces the default header text with an icon + label, and the default
              // CSS-only sort arrow with a small pill showing direction/priority.
              headerCell: () => {
                div(() => {
                  style("display", "flex");
                  style("align-items", "center");
                  style("gap", "6px");
                  text("📖");
                  text(translated("Title"));
                });
              },
              sortIndicator: state => {
                div(() => {
                  classes("table-demo__sort-pill");
                  text(state.map(s => s.sorted ? `${s.ascending ? "▲" : "▼"} ${s.priority}` : ""));
                });
              },
            }),
            column(translated("Author").get, (book) => text(book.author), { prefWidth: 220, minWidth: 100, maxWidth: 600, sortable: true, sortKey: "author", visible: showAuthors, onVisibilityChange: value => showAuthors.set(value) }),
          ]),
          columnGroup(translated("Details").get, [
            {
              text: translated("Year").get,
              // D05: `context` is this cell's own state -- the same focused/selected a custom
              // `row` renderer already gets for the whole row, here per cell.
              cell: (book: Book, context: TableCellContext) => {
                div(() => {
                  classIf("table-demo__year-cell--focused", context.focused);
                  classIf("table-demo__year-cell--selected", context.selected);
                  text(String(book.year));
                });
              },
              prefWidth: 100, minWidth: 70, maxWidth: 300, sortable: true, sortKey: "year",
              // C10: reaches the separately created header/cells, in addition to their own
              // ui-table-header-cell/ui-table-cell classes.
              headerClass: ["table-demo__numeric-header"], cellClass: ["table-demo__numeric-cell"],
            } satisfies ColumnDef<Book>,
            column(translated("Note").get, (book) => {
              const note = noteFor(book);
              input(() => {
                classes("table-demo__note");
                attr("aria-label", `${translated("Note").get}: ${book.title}`);
                attr("value", note);
                onInput((event) => note.set(inputValue(event)));
              });
            }, { prefWidth: 240, minWidth: 140, maxWidth: 600 }),
          ]),
        ],
        {
          rowKey: (book) => book.title,
          // V05: stays visible and keyboard-reachable, just not selectable/editable. Not reactive
          // like columnResizePolicy above -- set once, the same as row/customResizePolicy.
          rowDisabled: (book) => book.year < 2000,
          rowHeight: 40,
          tableMenuButtonVisible: true,
          columnMenuText: translated("Columns"),
          columnResizePolicy: resizePolicy,
          direction,
          selectionMode,
          cellSelectionEnabled,
          row: (row) => {
            classes("book-row");
            style("font-weight", row.selected.map((selected) => selected ? "600" : "400"));
            // V07: a per-row hover tooltip needs no dedicated "presenter" API -- the same
            // on/when/overlay recipe used for a column header's own menu, just anchored to a row.
            // Bound to row.item (not a snapshot), so it stays correct if virtualization later
            // rebinds this row to another book.
            const tooltipOpen = property(false);
            on("mouseenter", () => tooltipOpen.set(true));
            on("mouseleave", () => tooltipOpen.set(false));
            when(tooltipOpen, () => {
              overlay({ widthPx: 240 }, () => {
                div(() => {
                  classes("table-demo__row-tooltip");
                  attr("role", "tooltip");
                  text(row.item.map((book) => book === null ? "" : `${book.title} · ${book.author} · ${book.year}`));
                });
              });
            });
            row.renderCells();
          },
          crawlable: true,
          crawlId: "books",
          header: () => text(translated("Remote catalogue · visible rows load on demand")),
          placeholder: () => text(translated("No books found.")),
        }
      );
    });

    div(() => {
      classes("table-demo__stats");
      stat(translated("Selection mode"), table.selectionMode);
      stat(translated("Selection target"), table.cellSelectionEnabled.map(enabled => enabled ? "cells" : "rows"));
      stat(translated("Selected rows"), table.selectedIndices.map((indices) => String(indices.length)));
      stat(
        translated("Selected cells"),
        table.selectedCells.map((positions) => String(positions.filter(position => position.column >= 0).length)),
      );
      stat(translated("Focused row"), table.focusedIndex.map(index => index < 0 ? "—" : String(index + 1)));
      stat(
        translated("Focused cell"),
        table.focusedCell.map(position => position === null || position.column < 0 ? "—" : `${position.row + 1}:${position.column + 1}`),
      );
      div(() => {
        classes("table-demo__stat", "table-demo__stat--wide");
        div(() => {
          classes("table-demo__stat-label");
          text(translated("Selected book"));
        });
        div(() => {
          classes("table-demo__stat-value");
          when(
            table.selectedItem.map((book) => book === null),
            () => text(translated("No book selected")),
          );
          when(
            table.selectedItem.map((book) => book !== null),
            () => text(table.selectedItem.map((book) => book?.title ?? "")),
          );
        });
      });
    });

    div(() => {
      classes("table-demo__controls");

      controlGroup(
        translated("Sorting"),
        [translated("Shift-click headers to sort by multiple columns. Enter or Space sorts a focused header; Shift keeps other sort columns.")],
        () => {
          button(translated("Clear sorting"), {}, () => onClick(() => table.clearSort()));
          button(translated("Sort first two columns"), {}, () => onClick(() => table.setSortOrder([
            { columnIndex: 0, ascending: true }, { columnIndex: 1, ascending: false },
          ])));
          button(translated("Reload current sorting"), {}, () => onClick(() => table.sort()));
        },
      );

      controlGroup(
        translated("Selection"),
        [
          translated("Ctrl/Cmd-click toggles rows; Shift-click selects a range."),
          translated("In cell mode, Shift-click and Shift+Arrow select an inclusive rectangle."),
          translated("Focus the table: Up/Down, Home/End and PageUp/PageDown navigate rows; Left/Right enters and moves cell focus. Shift extends row selection; Ctrl/Cmd moves focus only; Space selects."),
          translated("Books before 2000 are disabled (V05): visible and keyboard-reachable, but not selectable or editable."),
          translated("Hover a row for its own tooltip (V07): a plain overlay anchored to the row, the same recipe as a column header's own menu."),
        ],
        () => {
          button(translated("Toggle single / multiple selection"), {}, () => {
            onClick(() => selectionMode.set(selectionMode.get === "multiple" ? "single" : "multiple"));
          });
          button(translated("Toggle row / cell selection"), {}, () => {
            onClick(() => cellSelectionEnabled.set(!cellSelectionEnabled.get));
          });
          button(translated("Clear book selection"), {}, () => onClick(() => table.clearSelection()));
        },
      );

      controlGroup(
        translated("Columns"),
        [
          translated("Drag a column edge to resize. Focus its grip and use arrow keys for keyboard resizing."),
          translated("Double-click a column edge to fit its content, or press Enter on the focused grip."),
          translated("Drag a column header to move it. Or focus the header and press Alt+Shift+Left/Right."),
          translated("The note field is bound per book. Its focus and text selection stay in place while neighboring columns change."),
          translated("Dragging the \"Book\" group's own edge resizes Title and Author together, spilling into Year only once both are at their own limit."),
          translated("The Year cell highlights itself when it is focused or selected (D05), using the same per-cell state a custom row already gets for the whole row."),
        ],
        () => {
          button(translated("Toggle author column"), {}, () => {
            onClick(() => showAuthors.set(!showAuthors.get));
          });
          button(translated("Toggle constrained / free column widths"), {}, () => onClick(() =>
            resizePolicy.set(resizePolicy.get === "unconstrained" ? "flex-last-column" : "unconstrained")));
          button(translated("Toggle left-to-right / right-to-left"), {}, () => onClick(() =>
            direction.set(direction.get === "ltr" ? "rtl" : "ltr")));
        },
      );

      controlGroup(
        translated("Navigation"),
        [translated("For horizontal navigation, use free widths and widen the columns.")],
        () => {
          button(translated("Go to row 500"), {}, () => onClick(() => table.scrollToIndex(499)));
          button(translated("Go to first row"), {}, () => onClick(() => table.scrollToIndex(0)));
          button(translated("Show selected row"), {}, () => onClick(() => table.scrollToIndex(table.selectedIndex.get)));
          button(translated("Show first column"), {}, () => onClick(() => table.scrollToColumnIndex(0)));
          button(translated("Show last column"), {}, () => onClick(() => table.scrollToColumnIndex(table.columnWidths.get.length - 1)));
        },
      );
    });
  });
}
