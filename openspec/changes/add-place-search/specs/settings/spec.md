# Spec Delta

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
  button `Clear browsed tiles` (offline-regions "Kept tiles", "Clear browsed tiles"); then
  `Reuse searches for` (`1 hour`, `1 day`, `7 days`, `30 days` or `365 days`) with the note
  `A search repeated within this time is answered without a request.`, and the button `Clear
  saved places` (place-search "Reuse of saved answers", "Clear saved places"; user decisions,
  2026-10-13).
- **Debug:** the switches `Timings`, `Tiles`, `Day state` and `Agreement check` ("Debug info"),
  in every build of the app.
- **About:** the app's version and the attributions (map-view "About and attributions").

A change of any setting SHALL take effect at once, without restarting the app.

#### Scenario: Defaults
- **WHEN** the Settings page is opened for the first time after installing the app
- **THEN** it shows `Decimal`, `Keep screen on` off, `Last view`, `60 %`, `Normal` without a `Custom resolution` entry, `512 MiB`, `30 days`, and `Timings`, `Tiles`, `Day state` and `Agreement check` off

#### Scenario: Order of the sections
- **WHEN** the Settings page is shown
- **THEN** its sections are Display, Map, Calculation, Storage, Debug and About, in this order, and the attributions are below every setting

#### Scenario: Back arrow
- **WHEN** the map shows Interlaken at zoom 12, the user opens the Settings page, scrolls to the About section and taps the back arrow
- **THEN** the map shows Interlaken at zoom 12, as with the system back gesture

#### Scenario: Change takes effect at once
- **WHEN** the user selects `Degrees, minutes, seconds` and goes back to the map
- **THEN** the coordinates are shown in degrees, minutes and seconds, without restarting the app

#### Scenario: Reuse period takes effect at once
- **WHEN** the user searched `eiger` online 2 days ago, selects `1 day` for `Reuse searches for`, and searches `eiger` again at the same place online
- **THEN** a request is sent, without restarting the app

### Requirement: Stored settings
Every setting, including the values of `Custom resolution` and the four debug switches, the last
view (map-view "Default viewport"), whether the panel's details are expanded (sun-position "Sun
information panel") and whether the first-run hint has been dismissed (map-view "First-run hint")
SHALL be kept in the app's persistent storage, across app
restarts and updates. They SHALL be read before the map screen is first shown, so that the first
map shown already follows them. When no value is stored for a setting, its default SHALL be used:
`Decimal`, `Keep screen on` off, `Last view`, `60 %`, `Normal`, the `Normal` values for `Custom
resolution`, `512 MiB`, `30 days` for `Reuse searches for`, every debug switch off, the details
collapsed and the hint not dismissed.
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

#### Scenario: Reuse period kept
- **WHEN** the user selects `7 days` for `Reuse searches for`, closes the app and launches it again
- **THEN** the Settings page shows `7 days`
