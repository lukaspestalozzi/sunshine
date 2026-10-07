## MODIFIED Requirements

### Requirement: Heatmap updates
The heatmap's day SHALL be computed in the background, like the day of sun-shade-overlay "Overlay
of the whole day" (the same CPU limit, stop and resume, and cache of days, kept apart from the
`Sun & shade` day). The heatmap SHALL be built off the main thread once every step of its day has
been computed; the map SHALL stay responsive meanwhile.
No heatmap of an area and date SHALL be shown before it is built; the app SHALL NOT show a partial
heatmap.

While the heatmap of the visible area and selected date is not built, in `Sun hours` mode:
- the notice `Computing sun hours …` SHALL be shown in the status card (sun-shade-overlay "Overlay
  status card"), and the progress of the heatmap's day on the time tape's strip (time-selection
  "Time tape strip");
- after a camera move, the previous heatmap SHALL stay on its geographic area until the new one is
  built; newly visible areas stay untinted meanwhile;
- after a change of the selected date, the previous heatmap SHALL stay until the new one is built.

A change of the selected time within the day SHALL NOT change the heatmap. When a day is computed
anew (sun-shade-overlay "Overlay of the whole day", "Cache of days"), its heatmap SHALL be built
again once the day is complete. A built heatmap SHALL be kept with its day in the cache of days.

#### Scenario: Switching on in heatmap mode
- **WHEN** the mode is `Sun hours`, the user switches the overlay on, and 36 of the heatmap day's 144 steps are computed
- **THEN** no heatmap is drawn, the status card shows `Computing sun hours …`, and the time tape's strip has the colours of those 36 steps and is not computed yet elsewhere in the daytime

#### Scenario: Day complete
- **WHEN** the last step of the heatmap's day has been computed
- **THEN** the heatmap is built and drawn, the notice disappears, and every step of the time tape's strip has its colour

#### Scenario: Time change
- **WHEN** the heatmap is shown and the user moves the time tape
- **THEN** the heatmap stays unchanged, without a notice

#### Scenario: Pan
- **WHEN** the heatmap is shown and the user pans the map by half a screen
- **THEN** the previous heatmap stays aligned with the terrain it was built for, and `Computing sun hours …` is shown until the heatmap of the new area replaces it

#### Scenario: Date change
- **WHEN** the heatmap is shown and the user picks another date whose day has not been computed
- **THEN** the previous heatmap stays and `Computing sun hours …` is shown until the heatmap of the new date replaces it

#### Scenario: Switching back to a computed day
- **WHEN** the heatmap of 2025-12-21 has been built, and the user picks 2025-12-22 and then 2025-12-21 again
- **THEN** the heatmap of 2025-12-21 is shown within 100 ms, without `Computing sun hours …` and without computing the day again
