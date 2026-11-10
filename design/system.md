# Design system — Parental Coordination (front)

Synced from the Kotlin Multiplatform `shared` module. Sources are Compose theme
files and the core component library; every value below was found in source, and
each entry names its source path. Dark-scheme values exist in source but the
artboards in this folder render the light scheme (the app's default).

## Colors

Source: `shared/src/commonMain/kotlin/com/parental/shared/core/ui/theme/Color.kt`

### Brand scheme (light, M3, teal seed #00696D)

| Token | Value | Usage |
| --- | --- | --- |
| `primary` | `#00696D` | Filled buttons, selected states, active nav indicator |
| `onPrimary` | `#FFFFFF` | Text/icons on `primary` |
| `primaryContainer` | `#9CF1F6` | Selected chip containers |
| `onPrimaryContainer` | `#002022` | Text on `primaryContainer` |
| `secondary` | `#4A6365` | Secondary actions |
| `secondaryContainer` | `#CCE8EA` | Tonal surfaces |
| `onSecondaryContainer` | `#051F21` | Text on `secondaryContainer` |
| `tertiary` | `#00697A` | Tertiary accents |
| `error` | `#BA1A1A` | Errors, destructive actions |
| `errorContainer` | `#FFDAD6` | Error surfaces |
| `background` / `surface` | `#FAFDFC` | Screen background |
| `onBackground` / `onSurface` | `#191C1D` | Primary text |
| `surfaceVariant` | `#DAE4E5` | Skeleton blocks, neutral fills |
| `onSurfaceVariant` | `#3F4849` | Secondary text, metadata |
| `outline` | `#6F797A` | Timeline line (4.37:1), fallback tag color |
| `outlineVariant` | `#BEC8C9` | Row separators (deliberately low contrast) |
| `inversePrimary` | `#80D4D9` | Dark-scheme primary |

### Category tags (6, light dots)

Source: `Color.kt` → `CategoryTokens.Light`

| Category (canonical ES) | `dot` | `container` | `content` |
| --- | --- | --- | --- |
| Salud | `#D81B60` | `#FCE4EC` | `#AD1457` |
| Educación | `#3F51B5` | `#E8EAF6` | `#303F9F` |
| Familiar | `#8E24AA` | `#F3E5F5` | `#6A1B9A` |
| Social | `#00897B` | `#E0F2F1` | `#00695C` |
| Recreación | `#F4511E` | `#FBE9E7` | `#B42C0E` |
| Otros | `#8D6E63` | `#EFEBE9` | `#5D4037` |

Unknown category → `outline`, never another category's `dot`.

### Status tags (activity subset of 11)

Source: `Color.kt` → `StatusTokens.Light`

| Status | `dot` | Label (ES) |
| --- | --- | --- |
| Created | `#757575` | Creada |
| Assigned | `#1E88E5` | Asignada |
| InProgress | `#E65100` | En progreso |
| Verify | `#B26A00` | Por verificar |
| Done | `#43A047` | Cumplida |
| Overdue | `#E53935` | Vencida |

`CANCELLED` normalizes to Created (grey); `COMPLETED` → Done.

## Typography

Source: `shared/src/commonMain/kotlin/com/parental/shared/core/ui/theme/Type.kt`

- Display + headline roles: platform **serif** (`FontFamily.Serif`), sizes 57 / 45 / 36 / 32 / 28 / 24.
- Title / body / label roles: platform **sans**. `titleMedium` 16/24 Medium, `titleSmall` 14/20 Medium, `bodyLarge` 16/24, `bodyMedium` 14/20, `bodySmall` 12/16, `labelLarge` 14/20 Medium, `labelMedium` 12/16 Medium, `labelSmall` 11/16 Medium with small caps (`smcp`).
- `TypeTokens.time`: **monospace** 12/16 Medium, letterSpacing 0.5 — the agenda time style.
- `TypeTokens.date`: serif headline (alias of `headlineSmall`, 24/32).

## Spacing and geometry

Source: `shared/src/commonMain/kotlin/com/parental/shared/core/ui/theme/Spacing.kt`

- Scale (only values new code may use): **4 / 6 / 8 / 12 / 16 / 24** (`xs/xxs/sm/md/lg/xl`).
- `hairline` 1 (timeline line, separators), `collar` 2 (dot gap), `padded` 32 (empty-state vertical padding), `maxContentWidth` 640.
- `AgendaGeometry`: `dateColumn` 48, `lineNode` 14 (timeline dot), `nodeCard` 40 (day-timeline node disc), `dotLegacy` 8 (category dot in day cell), touch target 44 (legacy) → 48, `undatedGutter` derived: 48 + 1 + 12 + 14 + 12 = 87.

## Shape

Source: `shared/src/commonMain/kotlin/com/parental/shared/core/ui/theme/Shape.kt`

- Radius scale: **4 / 8 / 12 / 16 / 24** (`extraSmall` → `extraLarge`; `extraLarge` deliberately 24, not M3's 28).
- Row bodies clip to `medium` (12). Tag chips use 12. `CircleShape` is spent (TagDot, node discs).

## Motion

Source: `shared/src/commonMain/kotlin/com/parental/shared/core/ui/theme/MotionTokens.kt`

- One duration: **150 ms** standard (`durationStandard`). Used by the two `Crossfade`s (mode switch, empty↔content). Skeleton is static (no shimmer — reduced-motion signal unreachable from commonMain, see DD-8).

## Component rules

Sources: `shared/src/commonMain/kotlin/com/parental/shared/core/ui/components/`

- **StatusTag / CategoryBadge** (`StatusTag.kt`, `CategoryBadge.kt`): 12dp-radius chip, container = tag color at **alpha 0.15**, text in the tag `dot` color, `labelSmall` + Medium, padding 8×4. Server `colorHex` wins over the token fallback for categories.
- **TagDot** (`TagDot.kt`): filled circle, `CircleShape`.
- **BottomNavBar** (`BottomNavBar.kt`): 4 items — Inicio, Actividades, Gastos, Terceros (M3 `NavigationBar`).
- **Agenda row** (`feature/activities/ui/AgendaTimelineRow.kt` + `ActivityBody.kt`): 48dp date column (day `titleMedium` SemiBold over abbreviated month `labelSmall`), 1dp `outline` timeline line, 14dp category dot, body = mono time + `bodyLarge` title (Medium, 2-line ellipsis; Done rows dim to `onSurfaceVariant`), optional deadline line, meta chips (`CategoryBadge` + `StatusTag`), `ConfirmarChip` sibling of the body (never inside the clickable area), `›` chevron when not confirmable.
- **Chrome** (`feature/activities/ui/ActivityFilters.kt` + `ActivitiesScreen.kt`): the
  top bar is `surface` with a 1dp `outlineVariant` hairline underneath; its actions are the
  view toggle (calendar-week icon ⇄ list icon) and "+". The filters are ONE horizontal
  row of `FilterChip`s — Todos / Eventos / Obligaciones — each with the count taken from
  the loaded list (`tabCounts`), horizontally scrollable. The old "Vista" segmented
  control and the "Vista" / "Filtrar" labels are gone: the view switch is the top-bar
  icon. Content capped at 640dp, horizontal padding 16.
- **EmptyState** (`CalendarChrome.kt`): `EventBusy` icon (decorative) + `titleMedium` title + optional body + optional button; one semantics node, action outside.
- **ListSkeleton** (`CalendarChrome.kt`): 4 static cards, `surfaceVariant` blocks, reuses the real timeline line.
- Accessibility contract: merged row semantics, decorative icons cleared, color never the only signal (weight + label carry status), touch targets ≥ 44dp (48 in new work), WCAG AA pairs verified in source comments.
