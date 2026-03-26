import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const css = readFileSync(new URL("./styles.css", import.meta.url), "utf8");

test("motion primitives are present", () => {
  assert.match(css, /@keyframes sectionRise/);
  assert.match(css, /@keyframes softPulse/);
  assert.match(css, /@keyframes sheenSweep/);
  assert.match(css, /\.motion-enter/);
  assert.match(css, /\.motion-enter\.is-visible/);
  assert.match(css, /\.motion-section\.is-visible/);
  assert.match(css, /\.motion-card\.is-visible/);
});

test("generation and loading states are present", () => {
  assert.match(css, /\.generator-button\.is-generating/);
  assert.match(css, /\.bottom-dock\.is-generating/);
  assert.match(css, /\.map-insight-card\.is-live::after/);
});

test("cta sheen and active-day emphasis are present", () => {
  assert.match(css, /\.primary-pill::after/);
  assert.match(css, /\.dock-cta::after/);
  assert.match(css, /\.primary-pill:hover::after/);
  assert.match(css, /\.dock-cta:hover::after/);
  assert.match(css, /\.is-day-active/);
});

test("reduced-motion fallback is present", () => {
  assert.match(css, /prefers-reduced-motion: reduce/);
  assert.match(css, /\.motion-enter,\s*\.motion-enter\.is-visible\s*{[\s\S]*opacity:\s*1;[\s\S]*transform:\s*none;[\s\S]*filter:\s*none;/);
});
