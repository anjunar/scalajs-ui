// Fails when a demo message has no German translation.
//
// Both demos resolve `i18n` messages against a catalog; a message without a `de` entry
// silently renders its English source under /de/. Nothing at compile time or in the test
// suites notices that, so this check compares the message literals in the page sources
// with the catalog literals -- for the Scala demo (`i18n"…"` in app/**, catalog in app/i18n/)
// and for the TypeScript demo (`i18n\`…\``, `translated("…")`, `message("…")` and the
// catalog metadata that reaches `translated()` at run time; catalog in **/translations.ts).
//
// Call: npm run check:i18n
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function filesUnder(directory, matches) {
  const result = [];
  for (const entry of readdirSync(directory)) {
    const path = join(directory, entry);
    if (statSync(path).isDirectory()) result.push(...filesUnder(path, matches));
    else if (matches(path)) result.push(path);
  }
  return result;
}

const normalize = (text) => text.replace(/\s+/g, " ").trim();

// ---------------------------------------------------------------------------
// Scala: a small lexer that knows comments, plain and triple-quoted strings and
// the `${…}` / `$name` splices of an interpolated string. Only `i18n"…"` and
// `i18nc"…"` literals are messages; every other string is skipped as a whole, so
// code samples that quote an i18n call do not count as usages.
// ---------------------------------------------------------------------------
function scalaMessages(source) {
  const messages = [];
  const isIdent = (c) => /[A-Za-z0-9_]/.test(c ?? "");
  let i = 0;
  const line = (at) => source.slice(0, at).split("\n").length;

  function skipBalanced(from) {
    // from points at `{`; returns the index after the matching `}`.
    let depth = 0;
    let j = from;
    while (j < source.length) {
      const c = source[j];
      if (c === '"') { j = skipString(j).end; continue; }
      if (c === "{") depth++;
      if (c === "}") { depth--; if (depth === 0) return j + 1; }
      j++;
    }
    throw new Error("Unbalanced `${` splice");
  }

  function skipString(from) {
    // from points at the opening quote; returns { end, text } with `text` normalized for
    // interpolated messages (`${…}` and `$name` become `{}`).
    const triple = source.startsWith('"""', from);
    let j = from + (triple ? 3 : 1);
    let text = "";
    while (j < source.length) {
      const c = source[j];
      if (triple) {
        if (source.startsWith('"""', j)) {
          let end = j + 3;
          while (source[end] === '"') end++; // `""""` closes with the extra quote inside
          text += source.slice(j + 3, end);
          return { end, text };
        }
      } else if (c === '"') {
        return { end: j + 1, text };
      } else if (c === "\\") {
        text += source[j + 1] === '"' ? '"' : source.slice(j, j + 2);
        j += 2;
        continue;
      }
      if (c === "$") {
        if (source[j + 1] === "{") { j = skipBalanced(j + 1); text += "{}"; continue; }
        if (source[j + 1] === "$") { text += "$"; j += 2; continue; }
        if (isIdent(source[j + 1])) { j += 1; while (isIdent(source[j])) j++; text += "{}"; continue; }
      }
      text += c;
      j++;
    }
    throw new Error("Unterminated string");
  }

  while (i < source.length) {
    const c = source[i];
    if (c === "/" && source[i + 1] === "/") { i = source.indexOf("\n", i); if (i < 0) break; continue; }
    if (c === "/" && source[i + 1] === "*") { i = source.indexOf("*/", i + 2); if (i < 0) break; i += 2; continue; }
    if (c === "'" && source[i + 2] === "'") { i += 3; continue; }
    if (c === '"') {
      const tagMatch = /(?:^|[^A-Za-z0-9_.])(i18nc?)$/.exec(source.slice(Math.max(0, i - 6), i));
      const { end, text } = skipString(i);
      if (tagMatch) {
        let key = normalize(text);
        if (tagMatch[1] === "i18nc") {
          const context = /^\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)/.exec(source.slice(end));
          if (context) key += `\u0000${context[1]}`;
        }
        messages.push({ key, line: line(i) });
      }
      i = end;
      continue;
    }
    i++;
  }
  return messages;
}

function checkScala() {
  const root = resolve(projectRoot, "scala/scalajs-ui-demo/src/main/scala-3");
  const catalog = new Set();
  const usages = [];
  for (const file of filesUnder(root, (path) => path.endsWith(".scala"))) {
    const isCatalog = file.includes(`${join("app", "i18n")}${file.includes("/") ? "/" : "\\"}`);
    for (const message of scalaMessages(readFileSync(file, "utf8"))) {
      if (isCatalog) catalog.add(message.key);
      else usages.push({ ...message, file: relative(projectRoot, file) });
    }
  }
  return { label: "Scala demo", catalog: catalog.size, usages, missing: usages.filter((u) => !catalog.has(u.key)) };
}

// ---------------------------------------------------------------------------
// TypeScript demo
// ---------------------------------------------------------------------------
function templateText(template) {
  if (ts.isNoSubstitutionTemplateLiteral(template)) return template.text;
  return [template.head.text, ...template.templateSpans.map((span) => `{}${span.literal.text}`)].join("");
}

function checkTypeScript() {
  const root = resolve(projectRoot, "npm/scalajs-ui-demo/src");
  const metadataFiles = new Set([resolve(root, "app/catalog.ts")]);
  const metadataProperties = new Set(["title", "summary"]);
  const catalog = new Set();
  const usages = [];
  for (const file of filesUnder(root, (path) => /\.tsx?$/.test(path) && !path.endsWith(".d.ts"))) {
    const isCatalog = /translations\.ts$/.test(file);
    const sourceFile = ts.createSourceFile(file, readFileSync(file, "utf8"), ts.ScriptTarget.Latest, true);
    const lineOf = (node) => sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line + 1;
    const record = (key, node) => {
      if (isCatalog) catalog.add(key);
      else usages.push({ key, line: lineOf(node), file: relative(projectRoot, file) });
    };
    const visit = (node) => {
      if (ts.isTaggedTemplateExpression(node) && ts.isIdentifier(node.tag) && node.tag.text === "i18n") {
        record(normalize(templateText(node.template)), node);
      }
      if (
        ts.isCallExpression(node) &&
        ts.isIdentifier(node.expression) &&
        (node.expression.text === "translated" || node.expression.text === "message") &&
        node.arguments.length >= 1 &&
        ts.isStringLiteralLike(node.arguments[0])
      ) {
        record(normalize(node.arguments[0].text), node);
      }
      // Catalog metadata (titles in the shell navigation, summaries on the search page)
      // reaches translated() with a runtime string; its literals are messages too.
      if (
        metadataFiles.has(file) &&
        ts.isPropertyAssignment(node) &&
        ts.isIdentifier(node.name) &&
        metadataProperties.has(node.name.text) &&
        ts.isStringLiteralLike(node.initializer)
      ) {
        record(normalize(node.initializer.text), node);
      }
      ts.forEachChild(node, visit);
    };
    visit(sourceFile);
  }
  return { label: "TypeScript demo", catalog: catalog.size, usages, missing: usages.filter((u) => !catalog.has(u.key)) };
}

let failed = false;
for (const result of [checkScala(), checkTypeScript()]) {
  console.log(`${result.label}: ${result.usages.length} messages, ${result.catalog} catalog entries, ${result.missing.length} without a German translation`);
  for (const usage of result.missing) {
    failed = true;
    console.log(`  ${usage.file}:${usage.line}: ${JSON.stringify(usage.key.replace("\u0000", " @ context "))}`);
  }
}
process.exit(failed ? 1 : 0);
