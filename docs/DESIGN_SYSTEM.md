# Design system — OffGrid Pop

> **Status: LOCKED (2026-10-02, P15).** Changes go through the `decision-log` skill.
> Source screens: `design/screens/pop/` (20 screens). Decisions: T15 (style), T16 (components).

NeoPOP-inspired (CRED's open-sourced design system), **inspired, not copied**: no CRED fonts, logos, colours-by-name or screens.

## 1. Principles
1. **Two-tone.** Every app screen = **dark hero band** (label + serif headline) over a **light body** (content + actions).
2. **Inversion marks secrets.** Revealed password, selected autofill login, security notes sit on **black inside the light body**.
3. **Sharp.** Corner radius **0** everywhere (cards, inputs, buttons, dialogs, sheets).
4. **Pop for primary actions only.** One pop button per screen (the main action); secondary actions are flat outline.
5. **Host apps keep their look.** Autofill UI inside other apps only styles our own suggestion chips.

## 2. Colour tokens
| Token | Hex | Use |
|---|---|---|
| `ink` | `#0D0D0D` | Dark band, primary buttons, text on light |
| `inkSurface` | `#161616` | Inputs/cards on dark |
| `inkHairline` | `#2A2A2A` | Borders on dark |
| `paper` | `#F1F1EE` | Light body background |
| `card` | `#FFFFFF` | Cards/inputs on light |
| `paperHairline` | `#D9D9D3` | Borders/dividers on light |
| `inputBorder` | `#BDBDB4` | Idle input border on light (focused = `ink`) |
| `textOnDark` | `#FFFFFF` / `#C9C9C9` body / `#A6A6A6` labels | |
| `textOnLight2` | `#55554F` | Secondary text/labels on light |
| `mint` | `#4DFFB4` | Accent **on dark** (and as a fill with `ink` text) |
| `mintEdgeBottom` / `mintEdgeRight` | `#1A7A55` / `#26A673` | Pop edges for mint buttons |
| `green` | `#0E7A50` | Accent **text on light** (links, "ON", numbers) |
| `coral` | `#FF7A5C` | Danger on dark |
| `coralDeep` | `#B8361C` | Danger on light / destructive button (white text) |
| `coralEdgeBottom` / `coralEdgeRight` | `#6E1F0F` / `#8F2914` | Pop edges for destructive button |
| `amber` | `#FFB547` | Warning fill (with `ink` text) |
| `inkEdgeBottom` / `inkEdgeRight` | `#4A4A4A` / `#7A7A7A` | Pop edges for black buttons on light |
| `whiteEdgeBottom` / `whiteEdgeRight` | `#6E6E6E` / `#A8A8A8` | Pop edges for white buttons on dark |
| `scrim` | `#8F8F8A` | Behind dialogs/sheets |

Contrast: all text pairs ≥ 4.5:1 (mint is never used as text on light — use `green`).

## 3. Typography
| Style | Font | Size / weight | Notes |
|---|---|---|---|
| `headline` | **Fraunces** (serif) | 40 sp (46 hero, 32–36 sheets) / 400, line-height 1.05 | **lowercase, ends with a full stop**: "vault locked." |
| `label` | **Manrope** | 10–11 sp / 800, tracking 2–3 sp | **UPPERCASE**: "STEP 2 OF 4", "HISTORY · 3 / 5" |
| `button` | Manrope | 12–14 sp / 800, tracking 2–3 sp | UPPERCASE |
| `body` | Manrope | 13–15 sp / 500–700 | Sentence case or lowercase |
| `secret` / `code` | **JetBrains Mono** | 14–17 sp / 500 | Passwords, recovery words, package names, numbers |

All three are OFL fonts from Google Fonts — **bundle them in the APK** (no downloadable fonts: that needs network).

## 4. Spacing & sizing
- Screen side padding **24 dp**; dark band top padding 48–64 dp (8 dp when it has a back button row).
- Gaps: 8 / 10 / 12 / 14 / 16 / 20 / 24 dp.
- Touch targets ≥ **44 dp** (icon buttons 44×44); primary button height **56 dp**; keypad keys 58–62 dp.
- Hairline 1 dp; input border 1.5 dp; focus/error border 1.5–2 dp.

## 5. Components (to build in Compose, T16)
| Component | Spec |
|---|---|
| **PopButton** | Rect, radius 0, height 56. Two bevelled edges, **6 dp**: bottom edge (parallelogram skewed 45°) + right edge (skewed 45°), colours per variant (§2). Variants: `ink` (on light), `white` (on dark), `mint`, `destructive`. Pressed: content offsets +6/+6 dp and edges collapse (the "plunk"). Reserve 6 dp right/bottom margin for edges |
| **OutlineButton** | Radius 0, 1.5 dp `ink` border, transparent, label style. Secondary actions |
| **HeroBand** | `ink` background, optional back/close row, `label` + `headline`, optional supporting text |
| **SharpCard** | `card` bg, 1 dp `paperHairline`, radius 0, padding 12–16 |
| **InverseCard** | `ink` bg on light body — for secrets/security notes; label in `mint` |
| **FieldLabel + SharpInput** | Label style above; 46–52 dp input, radius 0, `inputBorder` → `ink` when focused, `coralDeep` on error |
| **PinDots** | 6 squares 16 dp, filled `#FFFFFF` / empty 2 dp `#5A5A5A` border; error = `coral` border |
| **Keypad** | 3×4 grid, gap 10, white keys with hairline; fingerprint key = mint PopButton; backspace flat |
| **StatusStrip** | Full-width strip, label style: amber fill (warning) or coral outline (error) or mint outline (info on dark) |
| **CountdownBar** | 3 dp `mint` line under revealed password, shrinks over 20 s (S10) |
| **Monogram** | 44 dp square, 1.5 dp `ink` border, Fraunces lowercase initial |
| **Toast** | PopButton-shaped `ink` bar, label style, e.g. "COPIED · CLIPBOARD CLEARS IN 30S" |
| **Dialog / Sheet** | Dark band header + light body, radius 0, `SecureFlagPolicy.SecureOn` (T2) |

## 6. Iconography
Stroke icons, 1.8–2 dp, **square caps/joins** (matches sharp corners). 18–22 dp in 44 dp targets. No emoji.

## 7. Screen inventory (locked)
Setup: Welcome · Create PIN · Recovery intro · Recovery words · Type-back check · Words can/can't
Unlock: Unlock · Wrong PIN · Locked out · Enter recovery words
Vault: Vault list · Entry detail · Edit entry · Generator · Delete · Settings
Autofill: Locked suggestion · Matches · Link app (no match) · Save login

Placeholders in the mockups (`[word]`, `[Bank]`, `[N]`) are not copy.
