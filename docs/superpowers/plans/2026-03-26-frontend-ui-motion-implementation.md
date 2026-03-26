# Frontend UI Motion Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the travel planner UI to Chinese-first copy and add layered premium motion for load, generate, day switching, and map focus without breaking the existing API-driven workflow.

**Architecture:** Keep the current single-page React structure in `frontend/app.jsx`, but move pure formatting and motion-state logic into a small browser-safe helper module so it can be tested with `node --test`. Drive all animation through explicit class names and state flags in `app.jsx`, then implement the visual timing and reduced-motion behavior in `frontend/styles.css`.

**Tech Stack:** React 18 UMD + Babel standalone, plain CSS, Leaflet, Node built-in test runner

---

### Task 1: Add testable UI copy and motion helpers

**Files:**
- Create: `E:\CodeRepository\TapToGo\frontend\app-helpers.mjs`
- Create: `E:\CodeRepository\TapToGo\frontend\app-helpers.test.mjs`
- Modify: `E:\CodeRepository\TapToGo\frontend\index.html`

- [ ] **Step 1: Write the failing test**

```js
import test from "node:test";
import assert from "node:assert/strict";

import {
  describeDayZh,
  formatModeZh,
  formatTravelModeZh,
  getMotionClassName,
  getUiCopy
} from "./app-helpers.mjs";

test("ui copy is Chinese-first", () => {
  const copy = getUiCopy();

  assert.equal(copy.nav.generator, "行程生成");
  assert.equal(copy.nav.timeline, "行程时间线");
  assert.equal(copy.actions.generateIdle, "生成 AI 行程");
  assert.equal(copy.library.title, "已生成行程");
});

test("travel and planning labels are localized", () => {
  assert.equal(formatTravelModeZh("Metro and walking"), "地铁 + 步行");
  assert.equal(formatModeZh("openai-web-search"), "AI 联网生成");
  assert.equal(formatModeZh("demo"), "演示模式");
});

test("day description is Chinese", () => {
  const text = describeDayZh({
    activities: [{ name: "宽窄巷子" }, { name: "文殊院" }, { name: "春熙路" }]
  });

  assert.match(text, /从 宽窄巷子 开始/);
  assert.match(text, /最终回收到 春熙路/);
});

test("motion helper returns layered class names", () => {
  assert.equal(getMotionClassName("section", 0, true), "motion-enter motion-section motion-delay-0 is-visible");
  assert.equal(getMotionClassName("card", 3, false), "motion-enter motion-card motion-delay-3");
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test frontend/app-helpers.test.mjs`
Expected: FAIL with `Cannot find module` or missing export errors for `frontend/app-helpers.mjs`

- [ ] **Step 3: Write minimal implementation**

```js
const copy = {
  nav: {
    generator: "行程生成",
    timeline: "行程时间线",
    library: "行程库",
    map: "地图工作区"
  },
  actions: {
    start: "开始生成",
    generateIdle: "生成 AI 行程",
    generateLoading: "AI 正在生成安排...",
    refresh: "刷新数据",
    export: "导出行程单"
  },
  library: {
    title: "已生成行程",
    all: "全部",
    favorites: "收藏"
  }
};

export function getUiCopy() {
  return copy;
}

export function formatTravelModeZh(mode) {
  if (mode === "Public transit") return "公共交通";
  if (mode === "Metro and walking") return "地铁 + 步行";
  if (mode === "Ride-hailing and walking") return "打车 + 步行";
  if (mode === "Self-drive") return "自驾";
  return String(mode || "");
}

export function formatModeZh(mode) {
  if (mode === "openai-web-search") return "AI 联网生成";
  if (mode === "openai") return "AI 实时生成";
  if (mode === "demo") return "演示模式";
  if (mode === "demo-fallback") return "AI 降级回退";
  if (mode === "offline") return "离线";
  if (mode === "checking") return "检测中";
  return String(mode || "");
}

export function describeDayZh(day) {
  const first = String(day?.activities?.[0]?.name || "当天重点");
  const last = String(day?.activities?.[day?.activities?.length - 1]?.name || "收尾节点");
  const count = day?.activities?.length || 0;
  return `从 ${first} 开始，串联 ${count} 个执行节点，最终回收到 ${last}。`;
}

export function getMotionClassName(kind, delayIndex, visible) {
  const parts = ["motion-enter", `motion-${kind}`, `motion-delay-${delayIndex}`];
  if (visible) parts.push("is-visible");
  return parts.join(" ");
}

window.TapToGoUiHelpers = {
  getUiCopy,
  formatTravelModeZh,
  formatModeZh,
  describeDayZh,
  getMotionClassName
};
```

```html
<script type="module" src="./app-helpers.mjs"></script>
<script type="text/babel" data-presets="react" src="./app.jsx"></script>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test frontend/app-helpers.test.mjs`
Expected: PASS with 4 passing tests

- [ ] **Step 5: Commit**

```bash
git add frontend/app-helpers.mjs frontend/app-helpers.test.mjs frontend/index.html
git commit -m "feat: add chinese ui helper module"
```

### Task 2: Convert app copy to Chinese and wire motion state into the page

**Files:**
- Modify: `E:\CodeRepository\TapToGo\frontend\app.jsx`
- Modify: `E:\CodeRepository\TapToGo\frontend\app-helpers.test.mjs`
- Test: `E:\CodeRepository\TapToGo\frontend\app-helpers.test.mjs`

- [ ] **Step 1: Write the failing test**

Append these tests to `frontend/app-helpers.test.mjs`:

```js
import { buildMapSummaryZh, getStatusMessageZh } from "./app-helpers.mjs";

test("status messages are Chinese for loading and ready states", () => {
  assert.equal(
    getStatusMessageZh("loading"),
    "AI 正在生成具体安排，并同步补全地图点位与推荐信息..."
  );
  assert.equal(
    getStatusMessageZh("ready"),
    "新的 AI 行程已经落到时间线里，下面看到的就是可执行安排。"
  );
});

test("map summary is Chinese-first", () => {
  const summary = buildMapSummaryZh(
    {
      planning_sources: [{ title: "Source", url: "https://example.com" }],
      daily_itinerary: [{ day: 2, theme: "老城漫游", activities: [{ name: "人民公园" }, { name: "春熙路" }] }]
    },
    2
  );

  assert.equal(summary.title, "Day 2 路线焦点");
  assert.equal(summary.tag, "AI 洞察");
  assert.match(summary.description, /老城漫游/);
  assert.equal(summary.nextStop, "人民公园");
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test frontend/app-helpers.test.mjs`
Expected: FAIL with missing export errors for `buildMapSummaryZh` and `getStatusMessageZh`

- [ ] **Step 3: Write minimal implementation**

Update `frontend/app-helpers.mjs` with:

```js
export function getStatusMessageZh(kind) {
  if (kind === "loading") return "AI 正在生成具体安排，并同步补全地图点位与推荐信息...";
  if (kind === "ready") return "新的 AI 行程已经落到时间线里，下面看到的就是可执行安排。";
  if (kind === "history") return "已加载历史行程。切换卡片后，右侧地图和时间线会同步联动。";
  if (kind === "empty") return "还没有历史行程，先从上方生成器开始。";
  return "输入目的地后，AI 会直接生成到日程时间线、地图和住宿推荐里。";
}

export function buildMapSummaryZh(plan, selectedDay) {
  const activeDay =
    (plan?.daily_itinerary || []).find((day) => day.day === selectedDay) ||
    plan?.daily_itinerary?.[0];

  return {
    title: activeDay ? `Day ${activeDay.day} 路线焦点` : "当前区域洞察",
    tag: plan?.planning_sources?.length ? "AI 洞察" : "精选推荐",
    description: activeDay
      ? `今天围绕“${activeDay.theme}”展开，已把 ${activeDay.activities?.length || 0} 个安排压缩成一条更顺路的移动序列。`
      : "地图会根据当前选中的行程自动切换标记和路线。",
    nextStop: String(activeDay?.activities?.[0]?.name || "等待选择")
  };
}

Object.assign(window.TapToGoUiHelpers, {
  getStatusMessageZh,
  buildMapSummaryZh
});
```

Update `frontend/app.jsx` to pull helpers from `window.TapToGoUiHelpers`, replace visible English labels with Chinese, and add state-driven classes:

```jsx
const {
  getUiCopy,
  formatTravelModeZh,
  formatModeZh,
  describeDayZh,
  getMotionClassName,
  getStatusMessageZh,
  buildMapSummaryZh
} = window.TapToGoUiHelpers;

const copy = getUiCopy();

const [hasMounted, setHasMounted] = useState(false);
const [viewVersion, setViewVersion] = useState(0);

useEffect(() => {
  const timer = setTimeout(() => setHasMounted(true), 60);
  return () => clearTimeout(timer);
}, []);

useEffect(() => {
  if (selectedPlan) {
    setViewVersion((value) => value + 1);
  }
}, [selectedId, selectedDay]);
```

Apply these class patterns in JSX:

```jsx
<section className={`generator-panel ${getMotionClassName("section", 0, hasMounted)}`}>
<section className={`library-strip ${getMotionClassName("section", 1, hasMounted)}`}>
<section key={`hero-${viewVersion}`} className={`plan-hero ${getMotionClassName("section", 2, hasMounted)}`}>
<article className={`day-column ${selectedDay === day.day ? "active is-day-active" : ""} ${getMotionClassName("card", day.day, hasMounted)}`}>
<div className={`bottom-dock ${hasMounted ? "is-visible" : ""} ${loading ? "is-generating" : ""}`}>
```

Replace core UI strings with Chinese:

```jsx
<a href="#generator">{copy.nav.generator}</a>
<a href="#timeline" className="active">{copy.nav.timeline}</a>
<a href="#library">{copy.nav.library}</a>
<a href="#assistant">{copy.nav.map}</a>
<button className="primary-pill" type="button">{copy.actions.start}</button>
<h2>已生成行程</h2>
<div className="hero-summary-label">当前状态</div>
<span>下一站</span>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test frontend/app-helpers.test.mjs`
Expected: PASS with 6 passing tests

- [ ] **Step 5: Commit**

```bash
git add frontend/app.jsx frontend/app-helpers.mjs frontend/app-helpers.test.mjs
git commit -m "feat: localize planner ui and wire motion states"
```

### Task 3: Implement layered motion and reduced-motion support in CSS

**Files:**
- Create: `E:\CodeRepository\TapToGo\frontend\css-motion.test.mjs`
- Modify: `E:\CodeRepository\TapToGo\frontend\styles.css`
- Test: `E:\CodeRepository\TapToGo\frontend\css-motion.test.mjs`

- [ ] **Step 1: Write the failing test**

```js
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
});

test("generation and reduced-motion states are present", () => {
  assert.match(css, /\.generator-button\.is-generating/);
  assert.match(css, /\.map-insight-card\.is-live/);
  assert.match(css, /prefers-reduced-motion: reduce/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test frontend/css-motion.test.mjs`
Expected: FAIL because the keyframes and state selectors are not yet defined in `frontend/styles.css`

- [ ] **Step 3: Write minimal implementation**

Add these motion primitives to `frontend/styles.css`:

```css
@keyframes sectionRise {
  from {
    opacity: 0;
    transform: translate3d(0, 22px, 0);
    filter: blur(8px);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
    filter: blur(0);
  }
}

@keyframes softPulse {
  0%, 100% { box-shadow: 0 0 0 rgba(0, 87, 190, 0); }
  50% { box-shadow: 0 0 0 10px rgba(0, 87, 190, 0.10); }
}

@keyframes sheenSweep {
  from { transform: translateX(-140%); }
  to { transform: translateX(180%); }
}

.motion-enter {
  opacity: 0;
  transform: translate3d(0, 22px, 0);
  filter: blur(8px);
}

.motion-enter.is-visible {
  animation: sectionRise 680ms cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

.motion-delay-0.is-visible { animation-delay: 0ms; }
.motion-delay-1.is-visible { animation-delay: 80ms; }
.motion-delay-2.is-visible { animation-delay: 160ms; }
.motion-delay-3.is-visible { animation-delay: 240ms; }
.motion-delay-4.is-visible { animation-delay: 320ms; }

.generator-button,
.trip-card,
.day-column,
.activity-card,
.mini-card,
.day-tab,
.filter-chip,
.primary-pill,
.ghost-action {
  transition:
    transform 260ms ease,
    box-shadow 260ms ease,
    background-color 260ms ease,
    color 260ms ease,
    opacity 260ms ease;
}

.generator-button.is-generating,
.bottom-dock.is-generating {
  animation: softPulse 1.8s ease-in-out infinite;
}

.primary-pill {
  position: relative;
  overflow: hidden;
}

.primary-pill::after {
  content: "";
  position: absolute;
  inset: 0;
  width: 40%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.45), transparent);
  transform: translateX(-140%);
}

.primary-pill:hover::after,
.dock-cta:hover::after {
  animation: sheenSweep 900ms ease;
}

.map-insight-card.is-live::after {
  content: "";
  position: absolute;
  inset: -2px;
  border-radius: inherit;
  pointer-events: none;
  box-shadow: 0 0 0 1px rgba(0, 87, 190, 0.12);
  animation: softPulse 2.6s ease-in-out infinite;
}

.is-day-active {
  transform: translateY(-4px);
  box-shadow: 0 24px 42px rgba(36, 44, 81, 0.10);
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation: none !important;
    transition-duration: 1ms !important;
    scroll-behavior: auto !important;
  }

  .motion-enter,
  .motion-enter.is-visible {
    opacity: 1;
    transform: none;
    filter: none;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test frontend/css-motion.test.mjs`
Expected: PASS with 2 passing tests

- [ ] **Step 5: Commit**

```bash
git add frontend/styles.css frontend/css-motion.test.mjs
git commit -m "feat: add layered motion system to planner ui"
```

### Task 4: Verify source wiring and perform a browser smoke check

**Files:**
- Create: `E:\CodeRepository\TapToGo\frontend\source-smoke.test.mjs`
- Modify: `E:\CodeRepository\TapToGo\frontend\app.jsx`
- Test: `E:\CodeRepository\TapToGo\frontend\source-smoke.test.mjs`

- [ ] **Step 1: Write the failing test**

```js
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const app = readFileSync(new URL("./app.jsx", import.meta.url), "utf8");

test("app source is Chinese-first", () => {
  assert.match(app, /行程生成/);
  assert.match(app, /行程时间线/);
  assert.match(app, /已生成行程/);
  assert.match(app, /导出行程单/);
});

test("app source wires motion classes", () => {
  assert.match(app, /motion-enter/);
  assert.match(app, /is-day-active/);
  assert.match(app, /is-generating/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test frontend/source-smoke.test.mjs`
Expected: FAIL until `frontend/app.jsx` contains the final Chinese-first labels and motion state class names

- [ ] **Step 3: Write minimal implementation**

Finalize `frontend/app.jsx` source so it contains:

```jsx
<button className={`generator-button ${loading ? "is-generating" : ""}`} type="submit" disabled={loading}>
  {loading ? copy.actions.generateLoading : copy.actions.generateIdle}
</button>

<div className={`map-insight-card ${loading ? "is-live" : ""}`}>
```

And ensure the final visible labels include:

```jsx
<h2>已生成行程</h2>
<div className="hero-summary-label">当前状态</div>
<button type="button" className="primary-pill dock-cta">{copy.actions.export}</button>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test frontend/source-smoke.test.mjs`
Expected: PASS with 2 passing tests

- [ ] **Step 5: Commit**

```bash
git add frontend/app.jsx frontend/source-smoke.test.mjs
git commit -m "test: add source smoke checks for chinese planner ui"
```

### Task 5: Manual verification and responsive smoke check

**Files:**
- Modify: `E:\CodeRepository\TapToGo\frontend\app.jsx`
- Modify: `E:\CodeRepository\TapToGo\frontend\styles.css`

- [ ] **Step 1: Start the static frontend server**

Run: `node frontend/server.mjs`
Expected: prints `TapToGo frontend running at http://localhost:4173`

- [ ] **Step 2: Verify page and source endpoints**

Run: `powershell -Command "(Invoke-WebRequest 'http://127.0.0.1:4173/' -UseBasicParsing).StatusCode; (Invoke-WebRequest 'http://127.0.0.1:4173/app.jsx' -UseBasicParsing).StatusCode"`
Expected:

```text
200
200
```

- [ ] **Step 3: Perform browser checks**

Check these manually in the browser:

```text
1. Header, generator, library, map panel, and dock enter with staggered motion.
2. Navigation, buttons, chips, and dock copy are Chinese-first.
3. Clicking “生成 AI 行程” shows a visible loading state.
4. Switching Day updates the highlighted day card and map route.
5. Switching a history card updates hero, timeline, and right-side panels in sync.
6. Mobile width still stacks correctly and no Chinese labels overflow their containers.
```

- [ ] **Step 4: Fix any copy overflow or timing mismatch found during manual smoke**

If a chip or button wraps badly, adjust these CSS rules only:

```css
.site-nav a,
.filter-chip,
.day-tab,
.ghost-action,
.primary-pill,
.dock-cta {
  white-space: nowrap;
}

.header-search,
.dock-total strong,
.trip-card-meta,
.hero-meta {
  word-break: keep-all;
}
```

- [ ] **Step 5: Commit**

```bash
git add frontend/app.jsx frontend/styles.css
git commit -m "fix: tune chinese ui spacing and motion timing"
```

## Self-Review

### Spec coverage

- Chinese-first UI: Task 1 and Task 2
- restrained baseline motion: Task 3
- staged reveal after generation: Task 2 and Task 3
- selective tech accents: Task 3
- verification and responsive safety: Task 4 and Task 5

No spec gaps remain.

### Placeholder scan

- no `TODO`, `TBD`, or deferred placeholders remain
- each task includes exact file paths
- each code step contains concrete code
- each verification step includes an exact command and expected result

### Type consistency

- helper names are consistent across tasks:
  - `getUiCopy`
  - `formatTravelModeZh`
  - `formatModeZh`
  - `describeDayZh`
  - `getMotionClassName`
  - `getStatusMessageZh`
  - `buildMapSummaryZh`
- CSS state classes are consistent across tasks:
  - `motion-enter`
  - `is-visible`
  - `is-generating`
  - `is-live`
  - `is-day-active`
