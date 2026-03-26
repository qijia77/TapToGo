import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import {
  describeDayZh,
  describePlanModeZh,
  formatModeZh,
  formatTravelModeZh,
  getMotionClassName,
  getUiCopy
} from "./app-helpers.mjs";

const helperSource = readFileSync(
  new URL("./app-helpers.mjs", import.meta.url),
  "utf8"
);
const appSource = readFileSync(new URL("./app.jsx", import.meta.url), "utf8");

test("ui copy is Chinese-first", () => {
  const copy = getUiCopy();

  assert.equal(copy.nav.generator, "\u884c\u7a0b\u751f\u6210");
  assert.equal(copy.nav.timeline, "\u884c\u7a0b\u65f6\u95f4\u7ebf");
  assert.equal(copy.actions.generateIdle, "\u751f\u6210 AI \u884c\u7a0b");
  assert.equal(copy.library.title, "\u5df2\u751f\u6210\u884c\u7a0b");
});

test("travel and planning labels are localized", () => {
  assert.equal(
    formatTravelModeZh("Metro and walking"),
    "\u5730\u94c1 + \u6b65\u884c"
  );
  assert.equal(formatModeZh("openai-web-search"), "AI \u8054\u7f51\u751f\u6210");
  assert.equal(formatModeZh("demo"), "\u6f14\u793a\u6a21\u5f0f");
});

test("day description is Chinese", () => {
  const text = describeDayZh({
    activities: [
      { name: "\u5bbd\u7a84\u5df7\u5b50" },
      { name: "\u6587\u6b8a\u9662" },
      { name: "\u6625\u7199\u8def" }
    ]
  });

  assert.match(
    text,
    /\u4ece \u5bbd\u7a84\u5df7\u5b50 \u5f00\u59cb/
  );
  assert.match(
    text,
    /\u6700\u7ec8\u56de\u6536\u5230 \u6625\u7199\u8def/
  );
});

test("plan mode explanation distinguishes actual generation from capability", () => {
  assert.match(
    describePlanModeZh("demo-fallback", 0),
    /\u672c\u6b21\u6ca1\u6709\u76f4\u63a5\u91c7\u7528 AI \u8054\u7f51\u7ed3\u679c/
  );
  assert.match(
    describePlanModeZh("openai-web-search", 2),
    /\u5df2\u4fdd\u7559 2 \u6761\u8054\u7f51\u6765\u6e90/
  );
});

test("motion helper returns layered class names", () => {
  assert.equal(
    getMotionClassName("section", 0, true),
    "motion-enter motion-section motion-delay-0 is-visible"
  );
  assert.equal(
    getMotionClassName("card", 3, false),
    "motion-enter motion-card motion-delay-3"
  );
});

test("helper source stays ASCII-only", () => {
  assert.doesNotMatch(helperSource, /[^\x00-\x7F]/);
});

test("app wires Task 2 motion state and helper APIs", () => {
  assert.match(
    appSource,
    /const\s+\{[\s\S]*\}\s*=\s*window\.TapToGoUiHelpers \|\| \{\};/
  );
  assert.match(appSource, /getUiCopy/);
  assert.match(appSource, /formatTravelModeZh/);
  assert.match(appSource, /formatModeZh/);
  assert.match(appSource, /describeDayZh/);
  assert.match(appSource, /describePlanModeZh/);
  assert.match(appSource, /getMotionClassName/);
  assert.match(
    appSource,
    /const\s+\[\s*hasMounted\s*,\s*setHasMounted\s*\]\s*=\s*useState\(false\)/
  );
  assert.match(
    appSource,
    /const\s+\[\s*viewVersion\s*,\s*setViewVersion\s*\]\s*=\s*useState\(0\)/
  );
  assert.match(appSource, /getMotionClassName\("section",\s*0,\s*hasMounted\)/);
  assert.match(appSource, /key=\{`hero-\$\{viewVersion\}`\}/);
  assert.match(appSource, /getMotionClassName\(\s*"card",\s*day\.day,\s*hasMounted\s*\)/);
  assert.match(
    appSource,
    /bottom-dock \$\{hasMounted \? "is-visible" : ""\} \$\{loading \? "is-generating" : ""\}/
  );
});

test("app uses Chinese-first visible copy wiring", () => {
  assert.match(appSource, /<a href="#generator">\{copy\.nav\.generator\}<\/a>/);
  assert.match(
    appSource,
    /<a href="#timeline" className="active">\s*\{copy\.nav\.timeline\}\s*<\/a>/
  );
  assert.match(appSource, /<a href="#library">\{copy\.nav\.library\}<\/a>/);
  assert.match(appSource, /<a href="#assistant">\{copy\.nav\.map\}<\/a>/);
  assert.match(appSource, /className="primary-pill"[\s\S]*\{copy\.actions\.start\}/);
  assert.match(appSource, /\\u5df2\\u751f\\u6210\\u884c\\u7a0b/);
  assert.match(appSource, /\\u672c\\u6b21\\u751f\\u6210/);
  assert.match(appSource, /\\u540e\\u7aef\\u80fd\\u529b/);
  assert.match(appSource, /\\u4e0b\\u4e00\\u7ad9/);
  assert.match(appSource, /\\u6253\\u5f00\\u53c2\\u8003\\u68c0\\u7d22/);
});

test("app supports optional Chinese helper APIs for status and map summary", () => {
  assert.match(appSource, /getStatusMessageZh/);
  assert.match(appSource, /buildMapSummaryZh/);
  assert.match(appSource, /describePlanModeZh/);
});
