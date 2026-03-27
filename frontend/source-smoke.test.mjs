import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const app = readFileSync(new URL("./app.jsx", import.meta.url), "utf8");
const css = readFileSync(new URL("./styles.css", import.meta.url), "utf8");
const html = readFileSync(new URL("./index.html", import.meta.url), "utf8");

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

test("app source routes selection changes through focus-aware handlers", () => {
  assert.match(app, /function handlePlanSelect\(planId\)/);
  assert.match(app, /function handleDaySelect\(dayNumber\)/);
  assert.match(app, /function handleRecommendationSelect\(item\)/);
  assert.match(app, /scrollIntoView\(\{\s*behavior:\s*"smooth",\s*block:\s*"start"\s*\}\)/);
  assert.match(app, /onClick=\{\(\) => handlePlanSelect\(plan\.id\)\}/);
  assert.match(app, /onClick=\{\(\) => handleDaySelect\(day\.day\)\}/);
});

test("frontend switches map runtime to AMap loader", () => {
  assert.match(html, /webapi\.amap\.com\/loader\.js/);
  assert.doesNotMatch(html, /leaflet/i);
  assert.match(app, /window\._AMapSecurityConfig/);
  assert.match(app, /window\.AMapLoader/);
  assert.match(app, /AMapLoader\.load\(/);
  assert.match(app, /new AMap\.Marker\(/);
  assert.match(app, /mapInstance\.current\.resize\(\)/);
});

test("app source exposes map empty attribution details", () => {
  assert.match(app, /selectedPlan\.attribution/);
  assert.match(app, /className=\"map-empty-detail\"/);
});

test("app source falls back to destination center when no map entries resolve", () => {
  assert.match(app, /resolveDestinationFallbackPoint/);
  assert.match(app, /const destinationFallback = await resolveDestinationFallbackPoint\(AMap, plan\.destination\);/);
  assert.match(app, /const pointsToRender = nextPoints\.length \? nextPoints : destinationFallback \? \[destinationFallback\] : \[\];/);
});

test("app source retries map lookup without city limit and keeps day route matching numeric", () => {
  assert.match(app, /resolveMapEntryWithAmap/);
  assert.match(app, /resolveMapEntryLookup\(AMap, destination, keywords\)/);
  assert.match(app, /resolveMapEntryLookup\(AMap, "", keywords\)/);
  assert.match(app, /Number\(point\.day\) === Number\(selectedDay\)/);
});

test("app source renders grouped recommendation copy and focus state", () => {
  assert.match(app, /focusedPointKey/);
  assert.match(app, /buildRecommendationSections/);
  assert.match(app, /buildMapEntriesData/);
  assert.match(app, /legendKinds/);
});

test("app source renders Chinese-first map markers and legend", () => {
  assert.match(app, /getMapKindMeta/);
  assert.match(app, /className=\"map-legend\"/);
  assert.match(app, /className=\"legend-item\"/);
  assert.match(app, /className=\"legend-dot\"/);
});

test("app source exposes recommendation source badges and xiaohongshu link", () => {
  assert.match(app, /getRecommendationSourceLabel/);
  assert.match(app, /mini-card-source/);
  assert.match(app, /打开小红书参考/);
});

test("style source has Chinese text overflow guards", () => {
  assert.match(css, /white-space:\s*nowrap/);
  assert.match(css, /word-break:\s*keep-all/);
  assert.match(css, /\.map-legend/);
  assert.match(css, /\.legend-item/);
  assert.match(css, /\.mini-card\.is-active/);
  assert.match(css, /\.mini-card-group/);
  assert.match(css, /\.mini-card-source/);
});

test("app source does not leave unicode escapes in JSX text nodes", () => {
  assert.doesNotMatch(app, /<[A-Za-z][^>]*>\s*\\u[0-9A-Fa-f]{4}/m);
  assert.doesNotMatch(app, /<[A-Za-z][^>]*>\s*\\U[0-9A-Fa-f]{4}/m);
});
