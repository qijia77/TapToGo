import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import {
  buildMapEntriesData,
  buildPlaceKey,
  buildRecommendationSections,
  describeDayZh,
  describePlanModeZh,
  formatModeZh,
  formatTravelModeZh,
  getMapKindMeta,
  getMotionClassName,
  getUiCopy,
  isSelfDriveMode
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

test("grouped recommendation builder splits nearby, hot, and self-drive sections", () => {
  const sections = buildRecommendationSections(
    {
      travel_mode: "Self-drive",
      recommended_hotels: [
        {
          name: "\u7fe0\u6e56\u9152\u5e97",
          reason: "\u9002\u5408\u4f5c\u4e3a\u4f4f\u5bbf\u843d\u70b9"
        }
      ],
      recommended_food_nearby: [
        {
          name: "\u7fe0\u6e56\u7c73\u7ebf",
          reason: "\u79bb\u7b2c 1 \u5929\u666f\u70b9\u5f88\u8fd1",
          day: 1
        },
        {
          name: "\u6ec7\u6c60\u5c0f\u9986",
          reason: "\u79bb\u7b2c 2 \u5929\u666f\u70b9\u5f88\u8fd1",
          day: 2
        }
      ],
      recommended_food_hot: [
        {
          name: "\u8fc7\u6865\u7c73\u7ebf\u9986",
          reason: "\u7231\u5403\u699c\u5355\u70ed\u5ea6\u5f88\u9ad8"
        }
      ],
      recommended_parking: [
        {
          name: "\u5357\u95e8\u505c\u8f66\u573a",
          reason: "\u7b2c 1 \u5929\u505c\u8f66\u65b9\u4fbf",
          day: 1
        }
      ],
      recommended_refuel: [
        {
          name: "\u73af\u57ce\u52a0\u6cb9\u7ad9",
          reason: "\u9002\u5408\u7b2c 1 \u5929\u8865\u7ed9",
          day: 1
        }
      ],
      recommended_charging: [
        {
          name: "\u66f2\u6c5f\u5145\u7535\u7ad9",
          reason: "\u9002\u5408\u7b2c 2 \u5929\u8865\u80fd",
          day: 2
        }
      ],
      recommended_restaurants: []
    },
    1
  );

  assert.deepEqual(
    sections.map((section) => section.key),
    ["stay", "food-nearby", "food-hot", "drive-support"]
  );
  assert.equal(sections[1].title, "\u666f\u70b9\u9644\u8fd1");
  assert.equal(sections[1].items.length, 1);
  assert.equal(sections[1].items[0].mapKey, "food::\u7fe0\u6e56\u7c73\u7ebf::1");
  assert.equal(sections[2].title, "\u7206\u706b\u63a8\u8350");
  assert.equal(sections[3].groups.length, 2);
  assert.equal(sections[3].groups[0].key, "parking");
  assert.equal(sections[3].groups[1].key, "refuel");
});

test("marker metadata uses Chinese-first labels", () => {
  assert.equal(getMapKindMeta("spot").marker, "\u666f");
  assert.equal(getMapKindMeta("stay").marker, "\u5bbf");
  assert.equal(getMapKindMeta("food").marker, "\u98df");
  assert.equal(getMapKindMeta("charging").marker, "\u7535");
});

test("map entry builder emits grouped place kinds and keeps day filter", () => {
  const plan = {
    recommended_hotels: [
      { name: "\u7fe0\u6e56\u9152\u5e97", address: "\u7fe0\u6e56\u7247\u533a", latitude: 25.04, longitude: 102.71 }
    ],
    recommended_food_nearby: [
      { name: "\u7fe0\u6e56\u7c73\u7ebf", address: "\u7fe0\u6e56", latitude: 25.04, longitude: 102.70, day: 1 }
    ],
    recommended_food_hot: [
      { name: "\u8fc7\u6865\u7c73\u7ebf\u9986", address: "\u8001\u8857", latitude: 25.05, longitude: 102.72 }
    ],
    recommended_parking: [
      { name: "\u5357\u95e8\u505c\u8f66\u573a", address: "\u57ce\u5899\u5357\u4fa7", latitude: 34.23, longitude: 108.95, day: 1 }
    ],
    recommended_refuel: [
      { name: "\u73af\u57ce\u52a0\u6cb9\u7ad9", address: "\u4e8c\u73af\u5357\u8def", latitude: 34.20, longitude: 108.98, day: 1 }
    ],
    recommended_charging: [
      { name: "\u66f2\u6c5f\u5feb\u5145\u7ad9", address: "\u66f2\u6c5f\u65b0\u533a", latitude: 34.19, longitude: 108.99, day: 2 }
    ],
    recommended_restaurants: [],
    daily_itinerary: [
      {
        day: 1,
        activities: [
          { name: "\u57ce\u5899", address: "\u5357\u95e8", latitude: 34.26, longitude: 108.95 }
        ]
      }
    ]
  };

  const entries = buildMapEntriesData(plan, 1);

  assert.equal(entries.some((entry) => entry.kind === "stay"), true);
  assert.equal(entries.some((entry) => entry.kind === "food"), true);
  assert.equal(entries.some((entry) => entry.kind === "parking"), true);
  assert.equal(entries.some((entry) => entry.kind === "refuel"), true);
  assert.equal(entries.some((entry) => entry.kind === "charging"), false);
  assert.equal(entries.some((entry) => entry.kind === "spot"), true);
  assert.equal(isSelfDriveMode("Self-drive"), true);
  assert.equal(buildPlaceKey("charging", plan.recommended_charging[0]), "charging::\u66f2\u6c5f\u5feb\u5145\u7ad9::2");
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
