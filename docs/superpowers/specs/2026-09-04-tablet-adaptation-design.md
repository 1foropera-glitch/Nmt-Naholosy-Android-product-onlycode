# Tablet adaptation design

## Context

The app already has one tablet-aware behavior: `MainAppContent` replaces the bottom navigation bar with a navigation rail when the available width reaches 600 dp. The individual screens, however, still use phone layouts. Most lists, cards, paragraphs, and buttons expand across the remaining window, and the information-heavy screens do not use the extra horizontal space.

The tablet work must preserve the existing phone experience, state flow, navigation behavior, text, colors, and component styling. It must also work after rotation and in resizable or split-screen windows, so decisions are based on available width rather than the device type.

## Chosen approach

Use a shared, width-based Compose layout layer with three size classes:

- Compact: less than 600 dp. Keep the existing phone layout and bottom navigation.
- Medium: 600–839 dp. Use the navigation rail, wider outer padding, and centered content with a readable maximum width.
- Expanded: 840 dp and wider. Use the navigation rail and allow two-column layouts on information-heavy screens.

This is intentionally between two rejected extremes. Merely adding a maximum width would prevent awkward stretching but waste much of a landscape tablet. A full master-detail redesign would introduce new navigation and selection states that the current learning flow does not need.

## Shared layout API

Add a small `ui/layout` package that owns tablet layout policy:

- `AppWindowWidthSizeClass` classifies the current available width as compact, medium, or expanded.
- `rememberAppWindowWidthSizeClass()` exposes that classification to the top-level app and screens.
- `AdaptiveScreenContainer` fills the window, centers its child, preserves each compact screen's existing 16 or 24 dp gutter, applies 24 dp on medium windows and 32 dp on expanded windows, then limits content to a caller-selected maximum width.
- Shared constants define 760 dp focused-content, 960 dp form-content, and 1200 dp list/dashboard-content maximum widths. This avoids unrelated numeric breakpoints spreading across screens.

The classifier remains independent of Android framework types so it can be unit tested. Layout must react automatically when the window is resized or rotated.

## Navigation shell

`MainAppContent` will use the shared width classification instead of an isolated `screenWidthDp >= 600` check. Compact windows keep `BottomNavigationBar`; medium and expanded windows use `SideNavigationRail`. Active learning sessions and onboarding continue to hide both navigation components exactly as they do now.

The scaffold content must consume all remaining space beside the rail, including system and scaffold insets. No fixed activity orientation or tablet-only manifest entry is needed.

## Screen behavior

### Dashboard

- Compact and medium: retain the current single reading order.
- Expanded: use two balanced columns inside a 1200 dp maximum-width area.
- Keep the hero/header full width. Place the streak and today's plan in the first content band, then distribute the learning mode and quick-access content without changing their actions or test tags.
- Cards must not stretch beyond a comfortable reading width, and rows with actions must remain valid with increased system font size.

### Dictionary

- Keep the heading, search field, and letter filters full width within the shared list container.
- Compact and medium: one-column word list.
- Expanded: two-column word grid with stable spacing and the same list item component.
- Empty state spans all columns.

### Progress and difficult words

- Progress stays one column on compact and medium widths.
- On expanded widths, the summary/slide and difficult-words card form the primary column; repetition-stage cards form the secondary column. The destructive reset action remains visually separated at the end.
- Difficult Words uses one column below 840 dp and two columns at expanded width. Its heading and empty state span the available columns.

### Settings

- Center the settings feed in a maximum-width container instead of stretching cards across the tablet.
- Preserve its single-column reading order because account, theme, accessibility, reminder, purchase, and sync controls are sequential forms rather than dashboard data.
- Keep every switch and button at least 48 dp high and ensure long Ukrainian labels can wrap without overlapping controls.

### Practice setup, learning session, and completion

- Center these task-focused flows in a 760 dp maximum-width container.
- Do not turn answer choices into multiple columns: a single vertical answer order is more predictable and keeps feedback close to the selected answer.
- Practice setup becomes vertically scroll-safe for short landscape or split-screen windows.
- Session feedback, progress, exit dialog, and all existing animations and test tags remain unchanged.

### Onboarding

- Center pager content and navigation controls in the focused maximum-width container.
- Keep horizontal paging and the current three pages.
- Avoid tablet-wide paragraphs and tablet-wide primary buttons.

## Accessibility and interaction

- Preserve at least 48 dp touch targets and at least 8 dp between adjacent targets.
- Maximum widths constrain readability but never force a fixed width; narrow split-screen windows can shrink normally.
- Text is allowed to wrap. No content is clipped solely to keep a two-column layout.
- Existing semantic descriptions, live regions, reduced-motion behavior, and navigation test tags are retained.
- The layout must not depend on hover, pointer type, or physical device detection.

## Verification

Add unit coverage for width classification at boundary values: 599, 600, 839, and 840 dp. Add Compose layout or screenshot coverage for at least one compact phone width, a medium portrait tablet width, and an expanded landscape tablet width. Checks should cover navigation selection, maximum-width centering, and column count.

Per the current request, no Gradle build, test run, screenshot recording, or emulator launch will be performed during this implementation turn. Verification will therefore be limited to source inspection and static consistency checks until the user authorizes a build.

## Scope boundaries

This work does not change app data, ViewModel state, navigation destinations, billing, authentication, synchronization, ads, typography, colors, copy, or the learning algorithm. It also does not add a permanent drawer, a master-detail selection model, or new tablet-only features.
