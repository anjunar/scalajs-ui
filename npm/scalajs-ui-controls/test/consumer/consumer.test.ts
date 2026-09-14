/**
 * The consumer test for the controls package: does a foreign project get usable
 * controls by installing the tarballs and importing only through public
 * `exports`?
 *
 * Asserted here, and nothing else:
 *
 *  1. The packed tarballs install and resolve together.
 *  2. `@anjunar/scalajs-ui-controls` ships `dist` and its types, not `src`/`test`.
 *  3. `tsc --strict` with `skipLibCheck: false` over a file importing from
 *     core + bridge + controls -- the regression test for a broken shipped
 *     declaration.
 *  4. SSR of a tab strip and a local-source table against the linked Scala.js
 *     bridge produces the expected HTML.
 */
import { execFileSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

const packageRoot = resolve(process.cwd());
const repoRoot = resolve(packageRoot, "..", "..");
const corePackage = join(repoRoot, "npm", "scalajs-ui-core");
const bridgePackage = join(repoRoot, "npm", "scalajs-ui-bridge");
const linkedArtifact = join(bridgePackage, "dist", "fullopt", "main.js");

let consumer = "";

function run(command: string, args: readonly string[], cwd: string): string {
  return execFileSync(command, [...args], {
    cwd,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
}

function npm(args: readonly string[], cwd: string): string {
  const entry = process.env["npm_execpath"];
  if (entry !== undefined && entry.endsWith(".js")) {
    return run(process.execPath, [entry, ...args], cwd);
  }
  return execFileSync("npm", [...args], {
    cwd,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    shell: process.platform === "win32",
  });
}

function pack(directory: string, into: string): string {
  const output = npm(["pack", "--pack-destination", into, "--silent"], directory);
  const name = output.trim().split("\n").pop()!.trim();
  return join(into, name);
}

function fileSpecifier(path: string): string {
  return "file:" + path.replace(/\\/g, "/");
}

function lastJsonLine<T>(output: string): T {
  return JSON.parse(output.trim().split("\n").pop()!) as T;
}

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      "The Scala.js bridge is not linked. Run:\n\n" +
        '    sbt --server "scalajs-ui-bridge/fullLinkJS"\n\n' +
        "Expected: " +
        linkedArtifact
    );
  }

  consumer = mkdtempSync(join(tmpdir(), "scalajs-ui-controls-consumer-"));
  const tarballs = join(consumer, "tarballs");
  mkdirSync(tarballs);

  const coreTarball = pack(corePackage, tarballs);
  const bridgeTarball = pack(bridgePackage, tarballs);
  const controlsTarball = pack(packageRoot, tarballs);

  writeFileSync(
    join(consumer, "package.json"),
    JSON.stringify(
      {
        name: "scalajs-ui-controls-consumer-probe",
        private: true,
        version: "0.0.0",
        type: "module",
        dependencies: {
          "@anjunar/scalajs-ui-core": fileSpecifier(coreTarball),
          "@anjunar/scalajs-ui-bridge": fileSpecifier(bridgeTarball),
          "@anjunar/scalajs-ui-controls": fileSpecifier(controlsTarball),
        },
      },
      null,
      2
    )
  );

  // --legacy-peer-deps: core and controls both declare a peer on the CSS package
  // this probe does not install (it renders no stylesheet).
  npm(["install", "--no-audit", "--no-fund", "--legacy-peer-deps", "--silent"], consumer);
});

afterAll(() => {
  if (consumer !== "") rmSync(consumer, { recursive: true, force: true });
});

describe("a packed install", () => {
  it("ships dist and types, and nothing that should have stayed home", () => {
    const installed = join(consumer, "node_modules", "@anjunar", "scalajs-ui-controls");
    const entries = readdirSync(installed);

    expect(entries).toContain("dist");
    expect(entries).toContain("package.json");
    expect(entries).not.toContain("src");
    expect(entries).not.toContain("test");

    const dist = readdirSync(join(installed, "dist"));
    expect(dist).toContain("index.js");
    expect(dist).toContain("index.d.ts");
    expect(dist).toContain("table.d.ts");
  });
});

describe("typechecking a consumer", () => {
  it("resolves all three packages under --strict", () => {
    mkdirSync(join(consumer, "src"), { recursive: true });

    const source = [
      'import { div, installRuntime, listProperty, property, renderToString, text } from "@anjunar/scalajs-ui-core";',
      'import type { SsrResult } from "@anjunar/scalajs-ui-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-ui-bridge";',
      'import { carousel, checkBoxColumn, choiceBoxColumn, column, columnGroup, comboBoxColumn, convertingTextFieldColumn, dataGrid, progressBarColumn, remoteSource, tab, tableView, tabs, textFieldColumn, virtualList } from "@anjunar/scalajs-ui-controls";',
      'import type { ColumnDef, RemoteSource, TablePosition, TableSort, TableViewHandle, TableViewOptions, TableRowContext, TableSelectionMode, TextFieldBlurPolicy } from "@anjunar/scalajs-ui-controls";',
      "",
      "interface Row { readonly name: string }",
      "",
      "const columns: readonly ColumnDef<Row>[] = [",
      '  column("Name", (row) => text(row.name), { sortable: true, sortKey: "name", visible: true }),',
      "];",
      'const grouped: readonly ColumnDef<Row>[] = [columnGroup("Identity", columns)];',
      "",
      "export async function render(): Promise<SsrResult> {",
      "  installRuntime(bridgeRuntime);",
      "  return renderToString(() => {",
      "    const rows = listProperty<Row>([{ name: \"a\" }]);",
      '    const blur: TextFieldBlurPolicy = "keep";',
      '    const editableRows = listProperty([{ name: property("Ada"), active: property(true) }]);',
      '    tableView(editableRows, [textFieldColumn("Name", row => row.name, { editOnBlur: blur }), convertingTextFieldColumn("Length", row => row.name.map(value => value.length), text => Number.isInteger(Number(text)) ? { ok: true, value: Number(text) } : { ok: false, error: "Integer required" }), checkBoxColumn("Active", row => row.active), choiceBoxColumn("Role", row => row.name, ["Ada", "Grace"]), comboBoxColumn("Owner", row => row.name, ["Ada", "Grace"]), progressBarColumn("Progress", () => 0.5)], { editable: true });',
      "    tabs([tab(\"One\", () => div(() => text(\"one\")))]);",
      "    const options: TableViewOptions<Row> = { crawlable: true, crawlId: \"t\", cellSelectionEnabled: true, rowKey: row => row.name, row: (row: TableRowContext<Row>) => { const name: string | undefined = row.item.get?.name; row.renderCells(); } };",
      "    const table: TableViewHandle<Row> = tableView(rows, columns, options);",
      "    const focusedCell: TablePosition | null = table.focusedCell.get; table.focusCell(0, 0); table.focusRightCell(); table.focusBelowCell();",
      "    const menuOptions: TableViewOptions<Row> = { tableMenuButtonVisible: false, columnMenuText: \"Spalten\" };",
      "    if (!table.isDisposed) table.refresh();",
      "    table.selectIndex(0);",
      "    const selectedName: string | undefined = table.selectedItem.get?.name;",
      "    table.selectItem(rows.get[0]!);",
      "    table.clearSelection();",
      "    const mode: TableSelectionMode = \"multiple\";",
      "    table.setSelectionMode(mode);",
      "    table.setCellSelectionEnabled(true); table.selectCell(0, 0); table.selectCellRange(0, 0, 0, 0);",
      "    const selectedCells: readonly TablePosition[] = table.selectedCells.get;",
      "    const cellSelection: boolean = table.cellSelectionEnabled.get;",
      "    const selectedCell: boolean = table.isCellSelected(0, 0); table.clearCell(0, 0); table.clearAndSelectCell(0, 0);",
      "    table.selectIndices([0]); table.selectRange(0, 1); table.selectAll();",
      "    const selectedRows: readonly Row[] = table.selectedItems.get;",
      "    const selectedIndices: readonly number[] = table.selectedIndices.get;",
      "    table.clearIndex(0); table.clearAndSelect(0);",
      "    table.scrollToIndex(0); table.scrollToItem(rows.get[0]!);",
      "    table.scrollToColumnIndex(0);",
      "    table.focusIndex(0); table.focusNext(); table.focusPrevious();",
      "    const sorted: boolean = table.toggleSort(0, true);",
      "    const order: readonly TableSort[] = [{ columnIndex: 0, ascending: false }];",
      "    const ordered: boolean = table.setSortOrder(order);",
      "    const reloaded: boolean = table.sort();",
      "    const cleared: boolean = table.clearSort();",
      "    const sortField: string | undefined = table.sorting.get[0]?.field;",
      "    const focused: number = table.focusedIndex.get;",
      "    const focusedName: string | undefined = table.focusedItem.get?.name;",
      "    const widths: readonly number[] = table.columnWidths.get;",
      "    const visibleColumns: number = table.visibleColumnCount.get;",
      "    const cellData: unknown | null = table.getCellData(0, 0);",
      "    const cellValue = table.getCellObservableValue(0, 0)?.get;",
      "    const resized: boolean = table.resizeColumn(0, 10);",
      "    const moved: boolean = table.moveColumn(0, 1);",
      "    const fitted: boolean = table.autoFitColumn(0);",
      "    carousel(rows, (row) => div(() => text(row.name)));",
      "    dataGrid(rows, (row) => div(() => text(row?.name ?? \"\")));",
      "    virtualList(rows, (row) => div(() => text(row?.name ?? \"\")));",
      "    const remote: RemoteSource<Row, { offset: number }> = remoteSource({",
      "      initialQuery: { offset: 0 },",
      "      initial: [{ name: \"r\" }],",
      "      load: (q) => Promise.resolve({ items: [{ name: \"r\" }], offset: q.offset }),",
      "    });",
      "    tableView(remote, columns);",
      "    tableView(rows, grouped);",
      "  });",
      "}",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "src", "app.ts"), source);

    writeFileSync(
      join(consumer, "tsconfig.json"),
      JSON.stringify(
        {
          compilerOptions: {
            target: "ES2022",
            module: "ES2022",
            moduleResolution: "bundler",
            lib: ["ES2022", "DOM"],
            strict: true,
            noEmit: true,
            skipLibCheck: false,
          },
          include: ["src"],
        },
        null,
        2
      )
    );

    const tsc = join(repoRoot, "node_modules", "typescript", "bin", "tsc");
    expect(() => run(process.execPath, [tsc, "-p", "tsconfig.json"], consumer)).not.toThrow();
  });
});

describe("rendering controls from a packed install", () => {
  it("renders a tab strip and a table against the bridge", () => {
    const script = [
      'import { div, installRuntime, listProperty, renderToString, text } from "@anjunar/scalajs-ui-core";',
      'import { bridgeRuntime } from "@anjunar/scalajs-ui-bridge";',
      'import { tab, tableView, tabs } from "@anjunar/scalajs-ui-controls";',
      "installRuntime(bridgeRuntime);",
      "let selectedTitle;",
      "let selectedTitles;",
      "const result = await renderToString(() => {",
      "  const rows = listProperty([{ title: \"Dune\" }, { title: \"Solaris\" }]);",
      "  tabs([tab(\"A\", () => div(() => text(\"panel a\"))), tab(\"B\", () => div(() => text(\"panel b\")))], { selectedIndex: 1 });",
      "  const table = tableView(rows, [{ text: \"Title\", cell: (r) => text(r.title) }, { text: \"Hidden\", visible: false, cell: () => { throw new Error(\"Hidden cell rendered\"); } }], { crawlable: true, crawlId: \"probe\", row: (row) => { text(\"custom-row-content\"); row.renderCells(); } });",
      "  if (table.isDisposed) throw new Error(\"Handle disposed before unmount\");",
      "  table.refresh();",
      "  table.scrollToIndex(1); table.scrollToItem(rows.get[0]);",
      "  table.resizeColumn(0, 10); if (!Array.isArray(table.columnWidths.get)) throw new Error(\"Missing widths\");",
      "  table.selectIndex(1);",
      "  selectedTitle = table.selectedItem.get?.title;",
      "  table.setSelectionMode(\"multiple\"); table.selectIndices([0, 1]);",
      "  selectedTitles = table.selectedItems.get.map(row => row.title);",
      "});",
      "console.log(JSON.stringify({",
      "  status: result.status,",
      "  panelB: result.html.includes(\"panel b\"),",
      "  panelA: result.html.includes(\"panel a\"),",
      "  dune: result.html.includes(\"Dune\"),",
      "  solaris: result.html.includes(\"Solaris\"),",
      "  selectedTitle,",
      "  selectedTitles,",
      "  customRow: result.html.includes(\"custom-row-content\"),",
      "}));",
      "",
    ].join("\n");

    writeFileSync(join(consumer, "ssr-controls.mjs"), script);

    const result = lastJsonLine<{
      status: number;
      panelB: boolean;
      panelA: boolean;
      dune: boolean;
      solaris: boolean;
      selectedTitle: string;
      selectedTitles: string[];
      customRow: boolean;
    }>(run(process.execPath, ["ssr-controls.mjs"], consumer));

    expect(result.status).toBe(200);
    expect(result.panelB).toBe(true);
    expect(result.panelA).toBe(false);
    expect(result.dune).toBe(true);
    expect(result.solaris).toBe(true);
    expect(result.selectedTitle).toBe("Solaris");
    expect(result.selectedTitles).toEqual(["Dune", "Solaris"]);
    expect(result.customRow).toBe(true);
  });
});
