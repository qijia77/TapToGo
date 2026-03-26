import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const app = readFileSync(new URL("./app.jsx", import.meta.url), "utf8");
const css = readFileSync(new URL("./styles.css", import.meta.url), "utf8");

test("app source is Chinese-first", () => {
  assert.match(app, /\\u884c\\u7a0b\\u751f\\u6210/);
  assert.match(app, /\\u884c\\u7a0b\\u65f6\\u95f4\\u7ebf/);
  assert.match(app, /\\u5df2\\u751f\\u6210\\u884c\\u7a0b/);
  assert.match(app, /\\u5bfc\\u51fa\\u884c\\u7a0b\\u5355/);
});

test("app source wires motion classes", () => {
  assert.match(app, /motion-enter/);
  assert.match(app, /is-day-active/);
  assert.match(app, /is-generating/);
  assert.match(app, /is-live/);
});

test("style source has Chinese text overflow guards", () => {
  assert.match(css, /white-space:\s*nowrap/);
  assert.match(css, /word-break:\s*keep-all/);
});

test("app source does not leave unicode escapes in JSX text nodes", () => {
  assert.doesNotMatch(app, /<[A-Za-z][^>]*>\s*\\u[0-9A-Fa-f]{4}/m);
  assert.doesNotMatch(app, /<[A-Za-z][^>]*>\s*\\U[0-9A-Fa-f]{4}/m);
});
