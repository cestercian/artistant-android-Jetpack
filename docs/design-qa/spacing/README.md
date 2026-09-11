# Device-walked spacing audit (Sep 11, 2026)

The Sep-5 designer's-eye QA ([DESIGN_QA_2026-09.md](../../DESIGN_QA_2026-09.md)) was
code-grounded and says so: *"The screens were not walked on a device or emulator."*
This directory is that missing pass — every number here is measured from pixels on a
running build, not read off a source constant.

**Rig.** `artistant` AVD, Pixel 6, 1080×2400 at 420 dpi (**2.625 px/dp**), debug APK at
`main@229569e`, harness launches per RELEASE.md §10.

**Method.** `measure.py` finds horizontal bands of non-background pixels and reports each
band's left and right inset, its height and the gap to the next band, all in dp. Card and
tile extents are measured by flood-filling the fill colour; text insets are measured to the
first ink. Where a finding cites the design, the number comes from the extracted markup in
`design-2026-09/screens/`.

```bash
python3 measure.py c01-discover.png 2.625 110 0
```

## Measured clean

Worth recording, so nobody re-files these:

| Thing | Measured | Verdict |
|---|---|---|
| Page gutter, all eight tab roots | 20.2–22.1 dp (variation is glyph side-bearing) | consistent |
| Search "Browse by occasion" grid | 12.2 dp column gap, 12.2 dp row gap, 20.2 dp outer | even |
| Bookings card stack | 12.2 dp between every card | even |
| Account list separators | 20.2 dp both ends, 56.0 dp pitch | even |
| Tab bar height | 83.4 dp on every screen | consistent (§2 specifies 88) |
| Booking card action buttons | equal widths, 21.0 dp inset both sides | symmetric |
| Divider under the last conversation | present | design does the same (5 rows, 5 `border-bottom`) |
| Profile / artist-profile stat triples | column centres evenly spaced, rules centred between them | even |
| Account list rhythm (both scroll positions) | 20.2 dp rules, 56.0 dp plain rows, 61.0 dp with a subtitle | even |
| Notification / accessibility switch rows | rules 20.2 dp both ends, no orphan rules at section breaks | even |
| Pushed-screen title centring (6 of 7) | within 1.3–1.9 dp of the midpoint | centred |

### Second pass (Sep 11) — pushed settings screens

Account, Activity, Notifications, Language, Accessibility, Devices, Data export, Privacy. One finding (#192); the row rhythm, rule insets and section breaks all measured clean.

## Findings

| # | What | Evidence |
|---|---|---|
| 1 | Header top varies 23.2 dp across the eight tab roots; Gigs draws its title at 21sp against everyone else's 26sp | `issue-header-drift.png` |
| 2 | Studio's two standing cards differ by 26.6 dp in height where the design stretches them | `issue-studio-cards.png` |
| 3 | Messages thread dividers are inset 80 dp at the leading edge; the design runs them the full content width | `issue-messages-divider.png` |
| 4 | The artist-profile dock misses the design on four of its five paddings | `issue-dock.png` |
| 5 | Activity is the only one of seven pushed settings screens that does not centre its title | `issue-activity-header.png` |
