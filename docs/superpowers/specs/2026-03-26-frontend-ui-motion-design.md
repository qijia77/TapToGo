# TapToGo Frontend UI Motion Design

Date: 2026-03-26

## Goal

Refine the current frontend so the page is Chinese-first, visually more premium, and noticeably more alive without becoming noisy.

This iteration keeps the new overall layout that is already in place:

- top navigation
- left-side generator and timeline
- right-side map workspace
- bottom action dock

The work focuses on copy, motion, and interaction polish instead of another full layout reset.

## Product Intent

The current page structure is acceptable, but two issues remain:

1. Most user-facing copy is still English, which breaks the intended product tone.
2. Motion is too shallow and mostly limited to static hover states, so the UI does not yet feel like an AI planning workspace.

The updated experience should feel:

- Chinese-first in reading flow
- editorial and premium rather than dashboard-like
- animated in layers, not with constant loud motion
- clearly responsive to AI generation and itinerary switching

## Recommended Approach

Use a layered motion system that combines three animation styles in one hierarchy:

1. restrained baseline motion for the whole page
2. stronger staged reveals for generate and itinerary switching
3. selective tech-style highlights for AI states and map emphasis

This is the recommended approach because it satisfies the user's request for all three motion directions without collapsing into a noisy or cheap-looking experience.

## Scope

### In scope

- convert user-facing copy to Chinese-first wording
- preserve brand name `TapToGo`
- improve page-enter animation
- improve generator, timeline, map, and dock transitions
- add a visible "AI is working" state during generation
- improve selected day and selected trip switching feedback
- add lightweight tech accents to a few key elements

### Out of scope

- backend API changes
- data model changes
- replacing Leaflet
- changing the overall information architecture again

## Content Design

The page should read primarily in Chinese. English may remain only where it is part of the brand or a deliberate visual accent, but the default should be Chinese.

### Copy rules

- brand remains `TapToGo`
- navigation becomes Chinese
- section titles become Chinese
- buttons become Chinese
- status copy becomes Chinese
- card descriptions and empty states become Chinese
- generated data remains as returned by the backend unless translated locally by existing normalization logic

### Example wording direction

- `Generator` -> `行程生成`
- `Timeline` -> `行程时间线`
- `Library` -> `行程库`
- `Map Workspace` -> `地图工作区`
- `Generate AI itinerary` -> `生成 AI 行程`
- `Saved Trips` -> `已生成行程`
- `Current status` -> `当前状态`
- `Export itinerary` -> `导出行程单`

## Motion System

### 1. Baseline motion

Used across the page to make the UI feel polished but not aggressive.

- page sections fade in with a slight upward movement
- cards use soft transform and shadow transitions
- navigation underline and chips animate smoothly
- header and dock transitions use subtle blur and opacity changes

Target feeling:

- calm
- premium
- controlled

### 2. Staged reveal motion

Used when the user generates a trip or switches the active itinerary.

- generator panel enters first
- trip library follows
- hero summary enters next
- timeline day cards reveal with stagger
- map insight card and side panels reveal after timeline
- bottom dock settles in last

Target feeling:

- AI output is landing into the workspace
- the page is assembling a plan, not merely repainting

### 3. Tech accent motion

Used sparingly, only on high-value interaction points.

- CTA button gets a soft sweep highlight
- generator state gets a pulse or shimmer while loading
- active map marker pulses gently
- map insight card can use a faint breathing glow

Target feeling:

- intelligent
- live
- responsive

Guardrail:

These effects must be local and occasional. They must not run everywhere at once.

## Interaction Behavior

### Initial page load

- top header fades in first
- generator and map area enter with offset timing
- library and dock follow shortly after

### Generate action

- submit button changes to a loading visual state
- generator panel shows active AI status
- when data returns, timeline cards stagger in
- map panel updates after timeline reveal, not before

### Switch active trip

- selected trip card updates immediately
- hero area and timeline crossfade or slide slightly
- map insight panel updates with short delay to feel synchronized

### Switch day

- day chip transitions with stronger active feedback
- active day card gains emphasis
- map route updates with short fade timing

### Favorite toggle

- quick chip-scale response
- no large movement

## Visual Style Guardrails

- keep the existing light editorial palette
- preserve the no-line philosophy
- avoid neon, excessive glow, or gaming-style motion
- avoid large-scale parallax
- avoid infinite animations on every card
- respect reduced motion preferences

## Accessibility

- implement `prefers-reduced-motion` fallbacks
- do not rely on motion alone for active state indication
- maintain readable contrast in all animated states
- loading states should still expose text status

## Implementation Plan Shape

The implementation should likely touch:

- `frontend/app.jsx`
- `frontend/styles.css`

Potentially `frontend/index.html` only if an extra font or meta tweak is required, but this should be avoided unless necessary.

### Expected code changes

- replace English-first UI strings with Chinese-first strings
- add animation state classes for initial load, loading, selected day, and selection switching
- add CSS keyframes and transition tokens
- keep JS logic simple and mostly state-driven
- avoid introducing new dependencies

## Testing

Verification should cover:

- page loads without JSX errors
- generation still posts to `/api/trips/plan`
- history and favorite flows still work
- day switching still updates map route
- layout remains usable on desktop and mobile
- reduced-motion mode does not break the layout

## Risks

### Risk 1

Overusing motion can make the page feel cheap.

Mitigation:

Use baseline motion everywhere, but reserve strong motion only for generate and selection change.

### Risk 2

Chinese copy can break spacing or chip widths.

Mitigation:

Audit navigation, buttons, chips, and dock text after copy changes.

### Risk 3

Map and timeline updates can feel out of sync.

Mitigation:

Sequence the reveal intentionally so the hero and timeline settle just before the right-side map updates.

## Open Decision

No blocking product questions remain. The approved direction is:

- Chinese-first UI
- restrained premium baseline motion
- stronger staged reveal after generation
- selective tech accents on CTA, loading, and map focus

## Self-review

- no placeholders remain
- scope stays focused on UI copy and motion polish
- architecture and implementation direction are consistent
- motion hierarchy is explicit and not contradictory
