## MODIFIED Requirements

### Requirement: Settings page
The Settings page SHALL have a top bar with the title `Settings` and a back arrow with the content
description `Back`, at least 48 × 48 dp to touch; tapping it SHALL return to the map like the system
back gesture or button (settings "Settings button"; user decision, 2026-10-05). The top bar SHALL
stay visible while the page scrolls. Below it, the page SHALL show, from top to bottom, these
sections and entries, each entry with its current value:
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

#### Scenario: Back arrow
- **WHEN** the map shows Interlaken at zoom 12, the user opens the Settings page, scrolls to the About section and taps the back arrow
- **THEN** the map shows Interlaken at zoom 12, as with the system back gesture

#### Scenario: Change takes effect at once
- **WHEN** the user selects `Degrees, minutes, seconds` and goes back to the map
- **THEN** the coordinates are shown in degrees, minutes and seconds, without restarting the app

### Requirement: Custom resolution
The `Custom resolution` entry SHALL open a page with a top bar with the title `Custom resolution`
and a back arrow with the content description `Back`; tapping it SHALL leave the page like the
system back gesture or button. The page SHALL show four values, each chosen within its range:
- `Sun & shade cell`: 1 to 8 dp, in whole dp;
- `Sun & shade step`: 5, 10, 15, 20 or 30 min;
- `Sun hours cell`: 4 to 32 dp, in whole dp;
- `Sun hours step`: 5, 10, 15, 20 or 30 min.

Until the user changes them, the values SHALL be those of `Normal`. They SHALL be kept when
another preset is selected, and apply again when `Custom` is selected again. Changes on the page
SHALL take effect when the user leaves the page, so that adjusting a value does not start a
computation at every step of the adjustment.

#### Scenario: First use
- **WHEN** the user selects `Custom` for the first time and opens `Custom resolution`
- **THEN** the page shows `Sun & shade cell` 2 dp, `Sun & shade step` 5 min, `Sun hours cell` 8 dp and `Sun hours step` 10 min

#### Scenario: Ranges
- **WHEN** the user tries to set `Sun & shade cell` below 1 dp or `Sun hours cell` above 32 dp
- **THEN** the values stop at 1 dp and 32 dp respectively

#### Scenario: Back arrow applies the values
- **WHEN** the user sets `Sun & shade cell` to 4 dp on the `Custom resolution` page and taps the back arrow
- **THEN** the Settings page is shown, and the overlay uses 4 dp cells

#### Scenario: Kept across presets
- **WHEN** the user sets `Sun hours step` to 20 min, selects `Fast`, and then selects `Custom` again
- **THEN** `Sun hours step` is 20 min, and the heatmap counts every 20 minutes

### Requirement: Stored settings
Every setting, including the values of `Custom resolution` and the four debug switches, the last
view (map-view "Default viewport"), whether the panel's details are expanded (sun-position "Sun
information panel") and whether the first-run hint has been dismissed (map-view "First-run hint")
SHALL be kept in the app's persistent storage, across app
restarts and updates. They SHALL be read before the map screen is first shown, so that the first
map shown already follows them. When no value is stored for a setting, its default SHALL be used:
`Decimal`, `Keep screen on` off, `Last view`, `60 %`, `Normal`, the `Normal` values for `Custom
resolution`, `512 MiB`, every debug switch off, the details collapsed and the hint not dismissed.
The details' state and the hint are not shown on the Settings page. When a stored value cannot be read or is not
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
