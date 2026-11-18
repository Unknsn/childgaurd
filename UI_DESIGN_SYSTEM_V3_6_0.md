# SafeBand Guardian v3.6.0 — Design System Specification
## Human-Centered, Trustworthy & Accessible Child Safety Architecture

---

## 1. Design Philosophy & Principles

SafeBand Guardian v3.6.0 establishes an intentional, calm, and trustworthy visual identity for child safety wearable technology.

### Core Principles
1. **Calm Over Anxiety:** Child safety is an emotional domain. The interface avoids aggressive alarms, loud flashing borders, or grim dark telemetry terminals. It communicates protection, stability, and clarity.
2. **Hierarchy Over Density:** Essential information is immediately visible at a glance (child status, connection health, battery level, recent activity). Technical diagnostics (RSSI, hex MAC, raw sequence counters) are accessible via progressive disclosure rather than cluttering primary dashboards.
3. **Safety-First Ergonomics:** Critical actions (SOS trigger, emergency call, maintenance authorization) feature large, fail-safe touch targets ($\ge 48\text{dp}$ to $64\text{dp}$), distinct semantic colors, and accidental activation prevention (e.g. 3-second hold with haptic countdown).
4. **Human-Centered Readability:** Replaced monospaced terminal fonts with clean system sans-serif (`FontFamily.Default`), optimized line heights, and proportional letter spacing.

---

## 2. Color Palette & Semantic Behavior

| Token Name | Hex / Alpha | Role & Usage |
|---|---|---|
| **`PrimaryBrand`** | `#2563EB` | Royal Safety Blue — Primary CTA buttons, active tabs, brand accents. |
| **`PrimaryBrandDark`** | `#1D4ED8` | Deepened blue for pressed states and dark theme accents. |
| **`SecondaryBrand`** | `#4F46E5` | Deep Indigo — Secondary highlights and role distinctions. |
| **`Success`** | `#10B981` | Emerald Green — Device connected, strap protected, normal status. |
| **`SuccessSurface`** | `#ECFDF5` | Soft green tint for connected badges and positive status cards. |
| **`Warning`** | `#F59E0B` | Warm Amber — Stale telemetry ($>30\text{s}$), battery advisory, maintenance window. |
| **`WarningSurface`** | `#FFFBEB` | Soft amber tint for caution badges and maintenance banners. |
| **`Emergency`** | `#EF4444` | Controlled Rose Red — Active SOS broadcast, strap cut/tamper, boundary breach. |
| **`EmergencySurface`** | `#FEF2F2` | High-visibility emergency well with high-contrast text. |
| **`BgLight`** | `#F8FAFC` | Slate 50 — Calming, soft daylight background. |
| **`SurfaceLight`** | `#FFFFFF` | Pure White — Standard card surface in light mode. |
| **`BorderLight`** | `#E2E8F0` | Slate 200 — Subtle card outline and dividers. |
| **`BgDark`** | `#0A0F1D` | Deep Midnight Slate — Premium dark theme chassis without pure pitch black. |
| **`SurfaceDark`** | `#111827` | Gray 900 — Elevated surface cards in dark theme. |
| **`BorderDark`** | `#334155` | Slate 700 — Soft etched outlines in dark theme. |

---

## 3. Typography Scale (`SafeBandTypography.kt`)

| Style Role | Font Size | Line Height | Weight | Usage |
|---|---|---|---|---|
| **`displayLarge`** | 32sp | 40sp | Bold | Large numerical displays, countdown timer |
| **`displayMedium`** | 28sp | 36sp | Bold | Primary screen titles, child name hero |
| **`headlineLarge`** | 24sp | 32sp | Bold | Modal sheet titles, emergency banners |
| **`headlineMedium`** | 20sp | 28sp | SemiBold | Major section headers, card titles |
| **`titleMedium`** | 15sp | 22sp | Medium | Form field labels, list item titles |
| **`bodyLarge`** | 16sp | 24sp | Regular | Explanatory text, primary descriptions |
| **`bodyMedium`** | 14sp | 20sp | Regular | Standard card body, secondary descriptions |
| **`bodySmall`** | 12sp | 16sp | Regular | Metadata, timestamps, helper notes |
| **`labelLarge`** | 14sp | 20sp | SemiBold | Action buttons, prominent chips |
| **`labelMedium`** | 12sp | 16sp | Medium | Status badges, filter pills |
| **`labelSmall`** | 11sp | 14sp | Medium | Secondary badges, micro metadata |

---

## 4. Spacing, Layout & Touch Ergonomics (`SafeBandSpacing.kt`)

Based on a strict 4dp optical grid:
- **`xxs` (2dp) / `xs` (4dp):** Micro gaps between icon and text.
- **`sm` (8dp):** Inner badge padding, chip spacing.
- **`md` (12dp):** Sub-component padding, list item gaps.
- **`lg` (16dp):** Standard card padding, horizontal screen margins (`ScreenHorizontal`).
- **`xl` (20dp):** Space between major card groups.
- **`xxl` (24dp):** Major section separation.
- **`xxxl` (32dp):** Top hero spacing and empty state margins.
- **`MinTouchTarget` (48dp):** Enforced across all buttons, icon triggers, and interactive elements.

---

## 5. Shape Geometry (`SafeBandShapes.kt`)

- **Small (8dp):** Filter chips, text fields, small status badges.
- **Medium (12dp):** Action buttons, list cards, sub-panels.
- **Large (16dp):** Standard dashboard cards, child cards, maps.
- **Extra Large (24dp):** Hero overview cards, dialogs, bottom sheets.
- **Pill (999dp):** Status badges, floating quick-action pills.

---

## 6. Component Catalog (`SafeBandComponents.kt`)

1. **`SafeBandCard`:** Universal container with 16dp rounded corners, subtle border stroke, and 2dp elevation.
2. **`SafeBandHeroCard`:** Top summary card with 24dp radius and soft primary accent border.
3. **`SafeBandPrimaryButton`:** $\ge 48\text{dp}$ touch target, rounded 12dp, with integrated loading spinner.
4. **`SafeBandSecondaryButton`:** Outlined action button for secondary interactions.
5. **`SafeBandStatusBadge`:** Semantic status badge with colored circular dot (Green, Amber, Gray, Red, Blue).
6. **`SafeBandSectionHeader`:** Clean title + optional action link ("See all", "Edit").
7. **`SafeBandAvatar`:** Circular avatar with child's initials and real-time connectivity status dot.
8. **`SafeBandMetricChip`:** Glanceable metric pill (Battery, Safe Zone, Signal).
9. **`SafeBandTextField`:** Outlined text input with 12dp radius and inline validation.
10. **`SafeBandEmptyState`:** Clean empty state illustration, title, helper text, and CTA.
