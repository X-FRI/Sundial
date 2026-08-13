# Sundial Unified Design System Spec

**Date:** 2026-08-13

**Goal:** Move Sundial from platform-native mimicry and local UI patches to a single shadcn-inspired, Compose-native product design system that works consistently across desktop and mobile.

## Product Direction

Sundial is a local-first task workbench. It should feel calm, dense, precise, and owned by the product rather than generated from generic mobile or desktop templates.

The product should not chase full native visual parity per platform. Instead, it should keep one product language everywhere:

- Shared product concepts: Workbench, Lists, Analytics, Settings, Inbox, scheduled work, completed work, trash.
- Shared visual grammar: quiet surfaces, subtle borders, low-radius controls, restrained brand orange, semantic status tones.
- Platform adaptation only where ergonomics demand it: sidebar on desktop, bottom navigation on mobile, inspector on desktop, sheet on mobile.

## Information Architecture

Top-level navigation has three working destinations:

- **Workbench**: default first screen, all active work.
- **Lists**: user-created collections and inbox triage.
- **Analytics**: charts and output review.

Settings is a utility destination, not a top-level work mode.

Smart scopes such as Today, Scheduled, Completed, and Trash are not product-level destinations. They are secondary lenses inside Workbench. Mobile should not duplicate Workbench lenses in a horizontal chip strip above the ledger; the ledger Accordion is the primary expression.

## Navigation Rules

Desktop:

- Left sidebar shows product identity, search, top-level destinations, contextual secondary navigation, sync/settings utilities.
- Selecting Workbench maps to `Scope.All`.
- Selecting Lists maps to the current/first list when available, otherwise falls back to Workbench.
- Selecting Analytics maps to `Scope.Analytics`.
- Smart lenses may appear as a secondary sidebar group, but with quieter treatment than top-level destinations.

Mobile:

- Top bar shows product mark, current title, sync, add, settings, search.
- Bottom navigation only shows Workbench, Lists, Analytics.
- Workbench lenses are not shown as chips. Accordion sections carry their own counts.
- List selection can remain as a compact horizontal strip while the user is inside Lists.

Back and close:

- Top-level pages do not show a back button.
- Settings uses a compact chevron back action, not a generic text button.
- Desktop detail inspector uses close.
- Mobile detail sheet uses close and respects safe areas.
- Subtask details preserve parent-detail back behavior.

## Visual Language

The shadcn-inspired Sundial style is:

- Neutral surfaces with subtle borders.
- Low-radius controls, mostly 6-8dp.
- No heavy gradients, decorative cards, or oversized display typography.
- Orange is reserved for primary product accent and actionable focus.
- Status colors are semantic: danger/red, today/brand, warning/amber, success/green, info/blue, neutral/gray.
- Lists and ledgers should use the full available scroll area instead of floating containers that waste space.

## Core Components

The first implementation phase introduces or formalizes:

- `SundialDesignLanguage`: pure product navigation and presentation semantics.
- `SundialBackAction`: standard compact back affordance.
- Refined `ContextTimeline`: single compact summary grammar with distribution rail and legend.
- Refined mobile shell: no duplicate Workbench filter chip strip.
- Refined sidebar: top-level navigation visually separated from secondary lenses.
- Refined settings header: chevron back + title/subtitle.

## Acceptance Criteria

- Product top-level destinations are represented by one tested mapping layer.
- Mobile Workbench no longer shows duplicate Today/Scheduled/Completed/Trash filter chips above the ledger.
- Desktop sidebar clearly separates primary destinations from secondary Workbench lenses.
- Settings back action uses the unified chevron pattern.
- Timeline summary uses the same shadcn-like rail/legend pattern on desktop and mobile where applicable.
- Existing tests pass.
- Shared desktop compilation passes.
