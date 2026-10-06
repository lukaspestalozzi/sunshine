## MODIFIED Requirements

### Requirement: Settings page
The Settings page SHALL show, from top to bottom, these sections and entries, each entry with its
current value:
- **Display:** `Coordinates` (`Decimal`, `Degrees, minutes, seconds` or `Swiss grid LV95`;
  map-view "Selected location crosshair"); `Keep screen on` (a switch; "Keep screen on").
- **Map:** `Start at` (`Last view`, `My location` or `Alps overview`; map-view "Default
  viewport"); `Overlay opacity` (a slider from 20 % to 90 % in steps of 10 %, its value shown as
  e.g. `60 %`; sun-shade-overlay "Overlay appearance").
- **Calculation:** `Shade resolution` (`Fast`, `Normal`, `Detailed` or `Custom`; "Shade
  resolution"), and while `Custom` is selected, the entry `Custom resolution` ("Custom
  resolution").
- **Storage:** `Browsed tiles limit` (`128 MiB`, `256 MiB`, `512 MiB`, `1024 MiB` or `2048 MiB`)
  with the note `Lowering the limit removes the least recently used browsed tiles now.`, and the
  button `Clear browsed tiles` (offline-regions "Kept tiles", "Clear browsed tiles").
- **Debug:** the switches `Timings`, `Tiles`, `Day state` and `Agreement check` ("Debug info"),
  in every build of the app.
- **About:** the app's version and the attributions (map-view "About and attributions").

A change of any setting SHALL take effect at once, without restarting the app.

#### Scenario: Defaults
- **WHEN** the Settings page is opened for the first time after installing the app
- **THEN** it shows `Decimal`, `Keep screen on` off, `Last view`, `60 %`, `Normal` without a `Custom resolution` entry, `512 MiB`, and `Timings`, `Tiles`, `Day state` and `Agreement check` off

#### Scenario: Order of the sections
- **WHEN** the Settings page is shown
- **THEN** its sections are Display, Map, Calculation, Storage, Debug and About, in this order, and the attributions are below every setting

#### Scenario: Change takes effect at once
- **WHEN** the user selects `Degrees, minutes, seconds` and goes back to the map
- **THEN** the coordinates are shown in degrees, minutes and seconds, without restarting the app

### Requirement: Stored settings
Every setting, including the values of `Custom resolution` and the four debug switches, and the
last view (map-view "Default viewport") SHALL be kept in the app's persistent storage, across app
restarts and updates. They SHALL be read before the map screen is first shown, so that the first
map shown already follows them. When no value is stored for a setting, its default SHALL be used:
`Decimal`, `Keep screen on` off, `Last view`, `60 %`, `Normal`, the `Normal` values for `Custom
resolution`, `512 MiB`, and every debug switch off. When a stored value cannot be read or is not
one of the setting's allowed values, that setting SHALL use its default, and the Settings page
SHALL show that default; the other settings SHALL keep their stored values.

#### Scenario: Restart
- **WHEN** the user selects `Swiss grid LV95` and `Fast`, closes the app and launches it again
- **THEN** the Settings page shows `Swiss grid LV95` and `Fast`, and the coordinates on the map are in LV95

#### Scenario: Value not allowed
- **WHEN** the stored overlay opacity is 75 %, which is not one of the allowed values
- **THEN** the overlay opacity is 60 % and the Settings page shows `60 %`, and every other setting keeps its stored value

#### Scenario: Debug switch kept
- **WHEN** the user switches `Timings` on, closes the app and launches it again
- **THEN** `Timings` is on and the debug box is shown on the map

## ADDED Requirements

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
