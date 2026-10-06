## MODIFIED Requirements

### Requirement: Debug info
The four switches of the `Debug` section SHALL each show one group of values in a debug box on
the map. While at least one is on, the box SHALL be shown on the map screen in the left column
below the selected-location coordinates and the offline notice, in a monospace font on the
translucent background of the other labels. It SHALL NOT cover the crosshair, the sun information
panel, the buttons, the overlay toggle or the status card; where it does not fit, its lower lines
are cut off. While every switch is off, no box SHALL be shown, and no agreement check SHALL run.

Each group shows the values of the most recent computation of each kind, `–` before the first;
durations in whole milliseconds as `ms`, or in seconds with one decimal as `s` from 10 s up:
- **Timings:**
  - `Horizon <t> (tiles <t>)`: the last horizon profile of the selected location, in total and for
    loading its tiles;
  - `Sun periods <t>`: the last sun periods computed from a profile;
  - `Grid <t>, image <t>`: the last `Sun & shade` overlay of the selected time, its grid and its
    image;
  - `Day <n> steps (<m> night) <t>`: the last day of either overlay mode computed to its end, its
    steps, its night steps and its duration;
  - `Sun hours <t>, image <t>`: the last heatmap, its counting pass and its image.
- **Tiles:**
  - `Grid tiles <n>: <k> kept, <c> memory, <d> disk, <w> network, <u> unavailable`: the DEM tiles
    of the last overlay grid by where they came from;
  - `Horizon tiles <n>: <c> memory, <d> disk, <w> network, <u> unavailable`: the same for the last
    horizon profile;
  - `Memory <n> of <max> tiles`: the DEM tiles held in memory.
- **Day state:**
  - `Day <c>/<n> steps, <cell> dp / <step> min`: the day of the shown overlay mode, its computed
    steps of all, its cell size and step;
  - `Area <w>×<h> dp, zoom <z>`: its area, the zoom with one decimal;
  - `Reused <p> %`: the share of its area covered by a reused earlier day (sun-shade-overlay
    "Overlay of the whole day"), in whole percent; `0 %` when none is reused;
  - `Shown <source>`: where the shown `Sun & shade` overlay comes from: `own day`, `earlier day`
    (sun-shade-overlay "Overlay updates") or `previous overlay`;
  - `Cache <n> days, <m> of <max> MiB`: the cache of days.
- **Agreement check:** `Agreement <a> of <n> cells (<p> %)`: 3 s after a `Sun & shade` overlay is
  shown and stays, 50 of its cells are checked against point-sunshine "Sunshine at an instant" at
  their sample points; `…` while checking. A newer overlay cancels the check.

The box SHALL show a new value within 1 s. Collecting the values SHALL NOT slow the computations
they describe by more than 1 %.

#### Scenario: Off by default
- **WHEN** the app is launched for the first time after installing it
- **THEN** no debug box is shown on the map

#### Scenario: Timings of an overlay
- **WHEN** `Timings` is on, the overlay is switched on in the mode `Sun & shade` at map zoom 12, and the overlay of the selected time has been computed
- **THEN** the debug box shows a `Grid` line with the grid's and the image's durations in ms

#### Scenario: Only the groups switched on
- **WHEN** `Tiles` is on and `Timings`, `Day state` and `Agreement check` are off
- **THEN** the debug box shows the three tile lines and no other lines

#### Scenario: Earlier day shown after a pan
- **WHEN** `Day state` is on, the overlay is on in the mode `Sun & shade`, and after a pan the selected time is shown from an earlier day
- **THEN** the debug box shows `Shown earlier day`

#### Scenario: Agreement check only while on
- **WHEN** `Agreement check` is off and a `Sun & shade` overlay is shown for 10 s
- **THEN** no cell is checked against the point tracer

#### Scenario: Agreement check result
- **WHEN** `Agreement check` is on and a `Sun & shade` overlay at map zoom 12 has been shown for 3 s
- **THEN** the debug box shows `Agreement <a> of <n> cells (<p> %)` with n at most 50

#### Scenario: Release build
- **WHEN** the app is a release build
- **THEN** the Settings page shows the `Debug` section with its four switches

#### Scenario: Reuse shown
- **WHEN** `Day state` is on and after a half-screen pan the earlier day covers half of the new area
- **THEN** the debug box shows `Reused 50 %` (±2 %)
