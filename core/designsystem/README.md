# :core:designsystem

Odo's design system — the single source of truth for **colour, type, shape,
spacing, elevation, icon, and motion** tokens, plus the `OdoTheme` that provides
them. It sits at the bottom of the UI stack: any `:feature:*` or `:app` module
may depend on it, and shared UI (buttons, cards, badges) is promoted *here*, not
cross-imported between features (ROADMAP golden rule).

It re-exports Compose Material 3 transitively, so depending on
`:core:designsystem` gives a module Material **and** the Odo tokens in one line.

## The brand in one paragraph

Odo is "the dashboard the whole product lives near": a warm, near-black UI lit by
a single odometer-orange accent (`#E8743B`). The core motif is an
instrument-gauge ring (270° sweep) — logo, Health Score dial, confidence rings.
Type is **Inter**. The system is **dark-first**; a light theme mirrors it (the
accent darkens to `#D35F28` so white text passes AA contrast).

## Usage

Wrap the app once, above the nav host (already wired in `:shared` `App()`):

```kotlin
OdoTheme {            // darkTheme defaults to the system setting
    // …app…
}
```

Read tokens through the `OdoTheme` accessor — brand-faithful, not Material's
generic slots:

```kotlin
Text(
    text = "Apni gaadi",
    style = OdoTheme.typography.title,
    color = OdoTheme.colors.text,
)
Box(Modifier.background(OdoTheme.colors.surface, OdoTheme.shapes.card))
Spacer(Modifier.height(OdoTheme.spacing.lg))
val gauge = OdoTheme.colors.healthScoreColor(score)   // red < 50, amber < 80, green ≥ 80
```

`OdoTheme.materialColors` exposes the underlying Material `ColorScheme` for the
rare case a stock component needs a role directly.

## Token reference (`--odo-*` from the spec)

| Group | Accessor | Tokens |
| --- | --- | --- |
| Brand colours | `OdoTheme.colors` | `bg surface surfaceRaised border text textDim textMuted accent onAccent success warning danger` + `healthScoreColor(score)` |
| Typography | `OdoTheme.typography` | `display(52/700) title(24/700) heading(18/600) numeric(600·tnum) body(16/400) bodySmall(14/400) label(14/500) caption(12/600·+.12em)` |
| Shape | `OdoTheme.shapes` | `small(10) field(12) card(16) pill(∞) device(34)` |
| Spacing (4dp grid) | `OdoTheme.spacing` | `xs(4) sm(8) md(12) lg(16) xl(24) xxl(32) xxxl(48)` + layout: `screenEdge(20) cardPadding(16) cardGap(12) listRowVertical(11) minTouchTarget(48)` |
| Elevation | `OdoTheme.elevation` | `level0(0) level1(1) level2(3) level3(8)` |
| Icon | `OdoTheme.iconSizes` | `stroke(1.8) small(16) medium(20) large(24)` |
| Motion | `OdoTheme.motion` | `fastMillis(150) baseMillis(250) easeStandard(cubic .2,0,0,1)` |

## File map

| File | Holds |
| --- | --- |
| `theme/Color.kt` | Raw `OdoPalette` hexes + Material `Dark/LightColorScheme` mappings |
| `theme/OdoColors.kt` | `OdoColors` brand-token data class, dark/light instances, `healthScoreColor` |
| `theme/Type.kt` | `OdoFontFamily`, `OdoTypography` scale, Material `Typography` mapping |
| `theme/Shape.kt` | `OdoShapes` radii + Material `Shapes` mapping |
| `theme/Dimens.kt` | `OdoSpacing`, `OdoElevation`, `OdoIconSizes`, `OdoMotion` |
| `theme/Theme.kt` | `OdoTheme {}` composable + `OdoTheme` accessor object |

## Decisions / notes

- **Two-layer colour.** Brand-faithful screens use `OdoTheme.colors`; the Material
  `ColorScheme` exists so stock components never fall back to Material purple.
  `secondary`/`tertiary` carry the success/warning roles (the brand has no
  separate secondary hue).
- **Status colours are first-class**, not decoration: `success`/`warning`/`danger`
  drive Health Score, fairness verdicts, and verified/expired badges. Distinct
  from Material `error` (reserved for validation/UI failure) so they never get
  conflated. `danger` deliberately matches the "score < 50" spec label.
- **Inter is not yet bundled.** `OdoFontFamily` falls back to the platform font so
  the scale is correct today. To switch on Inter: add the weights to
  `commonMain/composeResources/font/`, then build the family with
  `FontFamily(Font(Res.font.inter_*, …))`. Every style reads from that one
  constant — nothing else changes.
- **`numeric`** ships at a 20sp base with tabular figures (`tnum`); resize per use
  (`.copy(fontSize = …)`) across the spec's 16–28 range for money/km/score.
- **`caption`** is tracked for UPPERCASE eyebrows — uppercase at the call site
  (the `TextStyle` doesn't transform text).
- **Dark elevation** is carried mainly by `surfaceRaised` + `border`, not shadows;
  the `OdoElevation` dp values map to Material `tonalElevation` where needed.
