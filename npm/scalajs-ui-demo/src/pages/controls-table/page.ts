import { attr, button, classes, div, element, onClick, onInput, property, style, text, when } from "@anjunar/scalajs-ui-core";
import { column, columnGroup, remoteSource, tableView } from "@anjunar/scalajs-ui-controls";
import type { Property, UiEvent } from "@anjunar/scalajs-ui-core";
import type { ColumnResizePolicy, RemotePage, RemoteSource, SortSpec, TableDirection, TableSelectionMode, TableViewHandle } from "@anjunar/scalajs-ui-controls";
import { translated } from "../../app/i18n.js";

const input = element("input");

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
    classes("flex", "flex-col", "gap-4");

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

    div(() => text(translated("Shift-click headers to sort by multiple columns. Enter or Space sorts a focused header; Shift keeps other sort columns.")));
    button(translated("Clear sorting"), {}, () => { onClick(() => table.clearSort()); });
    button(translated("Sort first two columns"), {}, () => onClick(() => table.setSortOrder([
      { columnIndex: 0, ascending: true }, { columnIndex: 1, ascending: false },
    ])));
    button(translated("Reload current sorting"), {}, () => onClick(() => table.sort()));
    button(translated("Toggle author column"), {}, () => {
      onClick(() => showAuthors.set(!showAuthors.get));
    });
    button(translated("Toggle single / multiple selection"), {}, () => {
      onClick(() => selectionMode.set(selectionMode.get === "multiple" ? "single" : "multiple"));
    });
    button(translated("Toggle row / cell selection"), {}, () => {
      onClick(() => cellSelectionEnabled.set(!cellSelectionEnabled.get));
    });
    div(() => text(translated("Ctrl/Cmd-click toggles rows; Shift-click selects a range.")));
    div(() => text(translated("In cell mode, Shift-click and Shift+Arrow select an inclusive rectangle.")));
    div(() => text(translated("Focus the table: Up/Down, Home/End and PageUp/PageDown navigate rows; Left/Right enters and moves cell focus. Shift extends row selection; Ctrl/Cmd moves focus only; Space selects.")));
    div(() => text(translated("Drag a column edge to resize. Focus its grip and use arrow keys for keyboard resizing.")));
    div(() => text(translated("Double-click a column edge to fit its content, or press Enter on the focused grip.")));
    div(() => text(translated("Drag a column header to move it. Or focus the header and press Alt+Shift+Left/Right.")));
    div(() => text(translated("The note field is bound per book. Its focus and text selection stay in place while neighboring columns change.")));
    button(translated("Toggle constrained / free column widths"), {}, () => onClick(() =>
      resizePolicy.set(resizePolicy.get === "unconstrained" ? "flex-last-column" : "unconstrained")));
    button(translated("Toggle left-to-right / right-to-left"), {}, () => onClick(() =>
      direction.set(direction.get === "ltr" ? "rtl" : "ltr")));
    button(translated("Go to row 500"), {}, () => onClick(() => table.scrollToIndex(499)));
    button(translated("Go to first row"), {}, () => onClick(() => table.scrollToIndex(0)));
    button(translated("Show selected row"), {}, () => onClick(() => table.scrollToIndex(table.selectedIndex.get)));
    div(() => text(translated("For horizontal navigation, use free widths and widen the columns.")));
    button(translated("Show first column"), {}, () => onClick(() => table.scrollToColumnIndex(0)));
    button(translated("Show last column"), {}, () => onClick(() => table.scrollToColumnIndex(table.columnWidths.get.length - 1)));

    div(() => {
      classes("table-page__table");
      style("height", "420px");
      style("min-height", "0");
      table = tableView(
        source,
        [
          columnGroup(translated("Book").get, [
            column(translated("Title").get, (book) => text(book.title), { prefWidth: 280, minWidth: 140, maxWidth: 900, sortable: true, sortKey: "title" }),
            column(translated("Author").get, (book) => text(book.author), { prefWidth: 220, minWidth: 100, maxWidth: 600, sortable: true, sortKey: "author", visible: showAuthors, onVisibilityChange: value => showAuthors.set(value) }),
          ]),
          columnGroup(translated("Details").get, [
            column(translated("Year").get, (book) => text(String(book.year)), { prefWidth: 100, minWidth: 70, maxWidth: 300, sortable: true, sortKey: "year" }),
            column(translated("Note").get, (book) => {
              const note = noteFor(book);
              input(() => {
                classes("w-full", "rounded-control", "border", "border-line", "px-2", "py-1");
                attr("aria-label", `${translated("Note").get}: ${book.title}`);
                attr("value", note);
                onInput((event) => note.set(inputValue(event)));
              });
            }, { prefWidth: 240, minWidth: 140, maxWidth: 600 }),
          ]),
        ],
        {
          rowKey: (book) => book.title,
          rowHeight: 40,
          tableMenuButtonVisible: true,
          columnMenuText: translated("Columns"),
          columnResizePolicy: resizePolicy,
          direction,
          selectionMode,
          cellSelectionEnabled,
          row: (row) => {
            classes("book-row");
            const book = row.item.get;
            if (book !== null) attr("title", `${book.title} · ${book.author} · ${book.year}`);
            style("font-weight", row.selected.map((selected) => selected ? "600" : "400"));
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
      classes("showcase-result");
      div(() => {
        div(() => text(translated("Selection mode")));
        div(() => text(table.selectionMode));
      });
      div(() => {
        div(() => text(translated("Selected rows")));
        div(() => text(table.selectedIndices.map((indices) => String(indices.length))));
      });
      div(() => {
        div(() => text(translated("Selection target")));
        div(() => text(table.cellSelectionEnabled.map(enabled => enabled ? "cells" : "rows")));
      });
      div(() => {
        div(() => text(translated("Selected cells")));
        div(() => text(table.selectedCells.map((positions) =>
          String(positions.filter(position => position.column >= 0).length))));
      });
      div(() => {
        div(() => text(translated("Focused row")));
        div(() => text(table.focusedIndex.map(index => index < 0 ? "—" : String(index + 1))));
      });
      div(() => {
        div(() => text(translated("Focused cell")));
        div(() => text(table.focusedCell.map(position =>
          position === null || position.column < 0 ? "—" : `${position.row + 1}:${position.column + 1}`)));
      });
      when(
        table.selectedItem.map((book) => book === null),
        () => text(translated("No book selected")),
      );
      when(
        table.selectedItem.map((book) => book !== null),
        () => text(table.selectedItem.map((book) => book?.title ?? "")),
      );
      button(translated("Clear book selection"), {}, () => onClick(() => table.clearSelection()));
    });
  });
}
