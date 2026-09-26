# Sun position reference values

Reference values for the `sun-position` spec of the change `add-sun-position` (roadmap #2), and
the measurements behind its refraction decision (design D2). Produced 2026-09-26.

## Sources

- **timeanddate.com**, via `sunrise-integration-test-plan.md` §4.1: sunrise, sunset and day length
  in Interlaken. These are the only published values. timeanddate answers HTTP 403 to automated
  requests, so no further published tables (twilight, position) could be fetched.
- **commons-suncalc 3.11**, the library the app uses. Script and output below.
- **astral 3.2** (Python, NOAA algorithm), an independent implementation. Script and output below.

Where no published value exists, the expected value is one on which both implementations agree.
They agree within 36 s on every Interlaken and Tokyo event and within 0.02° on every position.
The exception is apparent elevation at or below 0° geometric elevation (see "Refraction gap").

## Expected values and check

Observer: Interlaken, 46.6863° N, 7.8632° E, time zone Europe/Zurich, unless stated otherwise.

### Position (azimuth ±0.2°, elevation ±0.1°)

| Time | Expected az / el | suncalc az / el | astral az / el | Within tolerance |
|------|------------------|-----------------|----------------|------------------|
| 2025-12-21 12:00 +1 | 173.5 / 19.7 | 173.488 / 19.664 | 173.493 / 19.660 | both |
| 2025-06-21 15:00 +2 | 225.4 / 60.6 | 225.407 / 60.627 | 225.424 / 60.624 | both |
| 2025-12-21 08:20 +1 | – / 0.9 | 126.135 / 0.948 | 126.140 / 0.948 | both |
| 2025-12-21 08:11 +1 | – / -0.7 | 124.500 / -0.725 | 124.505 / -0.267 | suncalc only: refraction gap, see below |
| 2025-12-21 02:00 +1 | 46.9 / -60.1 | 46.941 / -60.131 | 46.951 / -60.127 | both |

"Above the horizon" means geometric elevation ≥ -0.833°:

| Time | Geometric el (suncalc / astral) | Expected |
|------|---------------------------------|----------|
| 2025-12-21 08:11 +1 | -0.725 / -0.724 | above |
| 2025-12-21 08:05 +1 | -1.578 / -1.577 | below |

### Events (±2 min)

| Date (zone) | Event | Expected | timeanddate | suncalc | astral |
|-------------|-------|----------|-------------|---------|--------|
| 2025-06-21 | civil dawn | 04:55 | – | 04:55:29 | 04:54:53 |
| | sunrise | 05:34 | 05:34 | 05:35:05 | 05:35:09 |
| | sunset | 21:25 | 21:25 | 21:26:11 | 21:25:40 |
| | civil dusk | 22:06 | – | 22:05:33 | 22:05:55 |
| 2025-12-21 | civil dawn | 07:35 | – | 07:35:07 | 07:34:36 |
| | sunrise | 08:10 | 08:10 | 08:10:14 | 08:10:32 |
| | sunset | 16:43 | 16:43 | 16:43:31 | 16:42:55 |
| | civil dusk | 17:19 | – | 17:18:39 | 17:18:52 |
| 2025-03-20 | sunrise | 06:31 | 06:31 | 06:31:31 | 06:31:37 |
| | sunset | 18:41 | 18:41 | 18:41:34 | 18:41:03 |
| 2025-09-22 | sunrise | 07:15 | 07:15 | 07:15:36 | 07:15:47 |
| | sunset | 19:25 | 19:25 | 19:26:11 | 19:25:47 |
| 2025-03-30 (DST starts) | sunrise | 07:12 +2 | – | 07:11:45 +2 | 07:11:55 +2 |
| | sunset | 19:55 +2 | – | 19:55:12 +2 | 19:54:46 +2 |
| 2025-10-26 (DST ends) | sunrise | 07:02 +1 | – | 07:01:58 +1 | 07:02:12 +1 |
| | sunset | 17:22 +1 | – | 17:22:32 +1 | 17:22:01 +1 |
| Tokyo 35.6762° N, 139.6503° E, 2025-06-21 (Asia/Tokyo) | sunrise | 04:26 | – | 04:26:02 | 04:26:06 |
| | sunset | 19:00 | – | 19:00:33 | 19:00:15 |

Every value lies within ±2 min of the expected value in both implementations.

### Day length (±2 min)

| Date | Expected | timeanddate | suncalc (set − rise) | astral (set − rise) |
|------|----------|-------------|----------------------|---------------------|
| 2025-06-21 | 15 h 51 min | 15 h 51 min | 15:51:06 | 15:50:31 |
| 2025-12-21 | 8 h 33 min | 8 h 33 min | 8:33:17 | 8:32:23 |

### Near-polar days (structural only)

Both implementations agree on which events exist, and on their order:

| Place, date (Europe/Oslo) | Result in both |
|---------------------------|----------------|
| Longyearbyen 78.2232° N, 15.6267° E, 2025-06-21 | no civil dawn, sunrise, sunset or civil dusk; sun above the horizon all day |
| Longyearbyen, 2025-12-21 | no events; sun below the horizon all day |
| Tromsø 69.6492° N, 18.9553° E, 2025-05-16 | sunrise, no sunset, no civil twilight |
| Tromsø, 2025-05-17 | sunset (00:07:46 suncalc, 00:03:40 astral) before sunrise (01:12:47 suncalc, 01:16:16 astral) |

The exact times differ by up to 4 min 6 s, because near the pole the sun crosses the horizon at a
shallow angle and small model differences move the crossing a lot. So these cases are not used
as time oracles.

## Refraction gap (design D2)

commons-suncalc's apparent elevation (`getAltitude()`) adds refraction only while the geometric
elevation is above 0°. At or below 0° it returns the geometric value (sweep below: `apparent`
equals `geometric` up to 08:14, then jumps by 0.467° by 08:17). Above 0° the library's refraction
matches the Sæmundsson formula (0.467° vs 0.466° at +0.12° geometric). astral applies refraction
below 0° too: at 08:11 it gives -0.267° apparent, where suncalc gives -0.725°.

The standard refraction missing at 0°, -0.5° and -1° geometric is 0.48°, 0.56° and 0.65°
(Sæmundsson). Near the horizon the sun climbs 0.14–0.17°/min at Interlaken
(0.25°/min · cos 46.69° · sin azimuth, for azimuths 54°–124°). So where a terrain horizon lies at
or below 0°, first sunshine computed with this elevation is 3–5 min late and last sunshine 3–5 min
early.

## Scripts

### commons-suncalc 3.11

Run with `javac -cp commons-suncalc-3.11.jar SunRefs.java && java -cp commons-suncalc-3.11.jar:. SunRefs`
(JDK 21).

```java
import org.shredzone.commons.suncalc.*;
import java.time.*;

// Reference outputs of commons-suncalc 3.11 for investigations/sun-position-references.md.
public class SunRefs {
    static final double LAT = 46.6863, LON = 7.8632;
    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    static void position(String local) {
        ZonedDateTime t = LocalDateTime.parse(local).atZone(ZURICH);
        SunPosition p = SunPosition.compute().on(t).at(LAT, LON).execute();
        System.out.printf("position %s %s  azimuth %.3f  apparent %.3f  geometric %.3f%n",
            local, t.getOffset(), p.getAzimuth(), p.getAltitude(), p.getTrueAltitude());
    }

    static SunTimes times(double lat, double lon, LocalDate d, ZoneId z, SunTimes.Twilight tw) {
        ZonedDateTime start = d.atStartOfDay(z);
        return SunTimes.compute().on(start).at(lat, lon)
            .limit(Duration.between(start, d.plusDays(1).atStartOfDay(z))).twilight(tw).execute();
    }

    static String t(ZonedDateTime z) { return z == null ? "absent" : z.toLocalDateTime() + z.getOffset().toString(); }

    static void day(String name, double lat, double lon, String date, String zone) {
        LocalDate d = LocalDate.parse(date); ZoneId z = ZoneId.of(zone);
        SunTimes v = times(lat, lon, d, z, SunTimes.Twilight.VISUAL);
        SunTimes c = times(lat, lon, d, z, SunTimes.Twilight.CIVIL);
        System.out.printf("day %s %s %s  dawn %s  rise %s  set %s  dusk %s  alwaysUp %s  alwaysDown %s%n",
            name, date, zone, t(c.getRise()), t(v.getRise()), t(v.getSet()), t(c.getSet()), v.isAlwaysUp(), v.isAlwaysDown());
    }

    public static void main(String[] a) {
        for (String s : new String[] {"2025-12-21T12:00", "2025-06-21T15:00", "2025-12-21T08:20", "2025-12-21T08:11",
                "2025-12-21T08:05", "2025-12-21T02:00"}) position(s);
        LocalDateTime sweep = LocalDateTime.parse("2025-12-21T07:50");
        for (int i = 0; i < 16; i++) position(sweep.plusMinutes(3L * i).toString());
        for (String d : new String[] {"2025-06-21", "2025-12-21", "2025-03-20", "2025-09-22", "2025-03-30", "2025-10-26"})
            day("Interlaken", LAT, LON, d, "Europe/Zurich");
        day("Tokyo", 35.6762, 139.6503, "2025-06-21", "Asia/Tokyo");
        day("Longyearbyen", 78.2232, 15.6267, "2025-06-21", "Europe/Oslo");
        day("Longyearbyen", 78.2232, 15.6267, "2025-12-21", "Europe/Oslo");
        day("Tromso", 69.6492, 18.9553, "2025-05-16", "Europe/Oslo");
        day("Tromso", 69.6492, 18.9553, "2025-05-17", "Europe/Oslo");
    }
}
```

Output:

```text
position 2025-12-21T12:00 +01:00  azimuth 173.488  apparent 19.664  geometric 19.617
position 2025-06-21T15:00 +02:00  azimuth 225.407  apparent 60.627  geometric 60.617
position 2025-12-21T08:20 +01:00  azimuth 126.135  apparent 0.948  geometric 0.534
position 2025-12-21T08:11 +01:00  azimuth 124.500  apparent -0.725  geometric -0.725
position 2025-12-21T08:05 +01:00  azimuth 123.420  apparent -1.578  geometric -1.578
position 2025-12-21T02:00 +01:00  azimuth 46.941  apparent -60.131  geometric -60.131
position 2025-12-21T07:50 +01:00  azimuth 120.756  apparent -3.756  geometric -3.756
position 2025-12-21T07:53 +01:00  azimuth 121.285  apparent -3.316  geometric -3.316
position 2025-12-21T07:56 +01:00  azimuth 121.816  apparent -2.877  geometric -2.877
position 2025-12-21T07:59 +01:00  azimuth 122.349  apparent -2.442  geometric -2.442
position 2025-12-21T08:02 +01:00  azimuth 122.883  apparent -2.008  geometric -2.008
position 2025-12-21T08:05 +01:00  azimuth 123.420  apparent -1.578  geometric -1.578
position 2025-12-21T08:08 +01:00  azimuth 123.959  apparent -1.150  geometric -1.150
position 2025-12-21T08:11 +01:00  azimuth 124.500  apparent -0.725  geometric -0.725
position 2025-12-21T08:14 +01:00  azimuth 125.042  apparent -0.302  geometric -0.302
position 2025-12-21T08:17 +01:00  azimuth 125.588  apparent 0.585  geometric 0.118
position 2025-12-21T08:20 +01:00  azimuth 126.135  apparent 0.948  geometric 0.534
position 2025-12-21T08:23 +01:00  azimuth 126.684  apparent 1.316  geometric 0.948
position 2025-12-21T08:26 +01:00  azimuth 127.236  apparent 1.689  geometric 1.359
position 2025-12-21T08:29 +01:00  azimuth 127.790  apparent 2.066  geometric 1.767
position 2025-12-21T08:32 +01:00  azimuth 128.347  apparent 2.444  geometric 2.172
position 2025-12-21T08:35 +01:00  azimuth 128.905  apparent 2.823  geometric 2.574
day Interlaken 2025-06-21 Europe/Zurich  dawn 2025-06-21T04:55:29+02:00  rise 2025-06-21T05:35:05+02:00  set 2025-06-21T21:26:11+02:00  dusk 2025-06-21T22:05:33+02:00  alwaysUp false  alwaysDown false
day Interlaken 2025-12-21 Europe/Zurich  dawn 2025-12-21T07:35:07+01:00  rise 2025-12-21T08:10:14+01:00  set 2025-12-21T16:43:31+01:00  dusk 2025-12-21T17:18:39+01:00  alwaysUp false  alwaysDown false
day Interlaken 2025-03-20 Europe/Zurich  dawn 2025-03-20T06:01:15+01:00  rise 2025-03-20T06:31:31+01:00  set 2025-03-20T18:41:34+01:00  dusk 2025-03-20T19:11:42+01:00  alwaysUp false  alwaysDown false
day Interlaken 2025-09-22 Europe/Zurich  dawn 2025-09-22T06:45:26+02:00  rise 2025-09-22T07:15:36+02:00  set 2025-09-22T19:26:11+02:00  dusk 2025-09-22T19:56:14+02:00  alwaysUp false  alwaysDown false
day Interlaken 2025-03-30 Europe/Zurich  dawn 2025-03-30T06:41:22+02:00  rise 2025-03-30T07:11:45+02:00  set 2025-03-30T19:55:12+02:00  dusk 2025-03-30T20:25:51+02:00  alwaysUp false  alwaysDown false
day Interlaken 2025-10-26 Europe/Zurich  dawn 2025-10-26T06:30:57+01:00  rise 2025-10-26T07:01:58+01:00  set 2025-10-26T17:22:32+01:00  dusk 2025-10-26T17:53:35+01:00  alwaysUp false  alwaysDown false
day Tokyo 2025-06-21 Asia/Tokyo  dawn 2025-06-21T03:55:52+09:00  rise 2025-06-21T04:26:02+09:00  set 2025-06-21T19:00:33+09:00  dusk 2025-06-21T19:30:48+09:00  alwaysUp false  alwaysDown false
day Longyearbyen 2025-06-21 Europe/Oslo  dawn absent  rise absent  set absent  dusk absent  alwaysUp true  alwaysDown false
day Longyearbyen 2025-12-21 Europe/Oslo  dawn absent  rise absent  set absent  dusk absent  alwaysUp false  alwaysDown true
day Tromso 2025-05-16 Europe/Oslo  dawn absent  rise 2025-05-16T01:28:36+02:00  set absent  dusk absent  alwaysUp false  alwaysDown false
day Tromso 2025-05-17 Europe/Oslo  dawn absent  rise 2025-05-17T01:12:47+02:00  set 2025-05-17T00:07:46+02:00  dusk absent  alwaysUp false  alwaysDown false
```

### astral 3.2

Run with `pip install astral==3.2 && python3 sun_refs.py` (Python 3.11).

```python
# Reference outputs of astral 3.2 (NOAA algorithm) for investigations/sun-position-references.md.
import datetime as dt
from zoneinfo import ZoneInfo
from astral import Observer
from astral.sun import azimuth, elevation, dawn, sunrise, sunset, dusk

LAT, LON = 46.6863, 7.8632
ZURICH = ZoneInfo("Europe/Zurich")

def position(local):
    t = dt.datetime.fromisoformat(local).replace(tzinfo=ZURICH)
    o = Observer(LAT, LON, 0)
    print(f"position {local} {t.strftime('%z')}  azimuth {azimuth(o, t):.3f}  apparent {elevation(o, t):.3f}"
          f"  geometric {elevation(o, t, with_refraction=False):.3f}")

def event(fn, o, d, z):
    try:
        return fn(o, date=d, tzinfo=z).strftime("%H:%M:%S%z")
    except ValueError as e:  # astral raises when the event does not occur
        return f"absent ({e})"

def day(name, lat, lon, date, zone):
    o, d, z = Observer(lat, lon, 0), dt.date.fromisoformat(date), ZoneInfo(zone)
    print(f"day {name} {date} {zone}  dawn {event(dawn, o, d, z)}  rise {event(sunrise, o, d, z)}"
          f"  set {event(sunset, o, d, z)}  dusk {event(dusk, o, d, z)}")

for s in ["2025-12-21T12:00", "2025-06-21T15:00", "2025-12-21T08:20", "2025-12-21T08:11", "2025-12-21T08:05", "2025-12-21T02:00"]:
    position(s)
for d in ["2025-06-21", "2025-12-21", "2025-03-20", "2025-09-22", "2025-03-30", "2025-10-26"]:
    day("Interlaken", LAT, LON, d, "Europe/Zurich")
day("Tokyo", 35.6762, 139.6503, "2025-06-21", "Asia/Tokyo")
day("Longyearbyen", 78.2232, 15.6267, "2025-06-21", "Europe/Oslo")
day("Longyearbyen", 78.2232, 15.6267, "2025-12-21", "Europe/Oslo")
day("Tromso", 69.6492, 18.9553, "2025-05-16", "Europe/Oslo")
day("Tromso", 69.6492, 18.9553, "2025-05-17", "Europe/Oslo")
```

Output:

```text
position 2025-12-21T12:00 +0100  azimuth 173.493  apparent 19.660  geometric 19.615
position 2025-06-21T15:00 +0200  azimuth 225.424  apparent 60.624  geometric 60.615
position 2025-12-21T08:20 +0100  azimuth 126.140  apparent 0.948  geometric 0.535
position 2025-12-21T08:11 +0100  azimuth 124.505  apparent -0.267  geometric -0.724
position 2025-12-21T08:05 +0100  azimuth 123.425  apparent -1.367  geometric -1.577
position 2025-12-21T02:00 +0100  azimuth 46.951  apparent -60.127  geometric -60.130
day Interlaken 2025-06-21 Europe/Zurich  dawn 04:54:53+0200  rise 05:35:09+0200  set 21:25:40+0200  dusk 22:05:55+0200
day Interlaken 2025-12-21 Europe/Zurich  dawn 07:34:36+0100  rise 08:10:32+0100  set 16:42:55+0100  dusk 17:18:52+0100
day Interlaken 2025-03-20 Europe/Zurich  dawn 06:00:53+0100  rise 06:31:37+0100  set 18:41:03+0100  dusk 19:11:52+0100
day Interlaken 2025-09-22 Europe/Zurich  dawn 06:44:58+0200  rise 07:15:47+0200  set 19:25:47+0200  dusk 19:56:31+0200
day Interlaken 2025-03-30 Europe/Zurich  dawn 06:40:50+0200  rise 07:11:55+0200  set 19:54:46+0200  dusk 20:25:57+0200
day Interlaken 2025-10-26 Europe/Zurich  dawn 06:30:24+0100  rise 07:02:12+0100  set 17:22:01+0100  dusk 17:53:46+0100
day Tokyo 2025-06-21 Asia/Tokyo  dawn 03:55:25+0900  rise 04:26:06+0900  set 19:00:15+0900  dusk 19:30:56+0900
day Longyearbyen 2025-06-21 Europe/Oslo  dawn absent (Sun never reaches 6 degrees below the horizon, at this location.)  rise absent (Sun is always above the horizon on this day, at this location.)  set absent (Sun is always above the horizon on this day, at this location.)  dusk absent (Sun never reaches 6 degrees below the horizon, at this location.)
day Longyearbyen 2025-12-21 Europe/Oslo  dawn absent (Sun never reaches 6 degrees below the horizon, at this location.)  rise absent (Sun is always below the horizon on this day, at this location.)  set absent (Sun is always below the horizon on this day, at this location.)  dusk absent (Sun never reaches 6 degrees below the horizon, at this location.)
day Tromso 2025-05-16 Europe/Oslo  dawn absent (Sun never reaches 6 degrees below the horizon, at this location.)  rise 01:30:57+0200  set absent (Unable to find a sunset time on the date specified)  dusk absent (Sun never reaches 6 degrees below the horizon, at this location.)
day Tromso 2025-05-17 Europe/Oslo  dawn absent (Sun never reaches 6 degrees below the horizon, at this location.)  rise 01:16:16+0200  set 00:03:40+0200  dusk absent (Sun never reaches 6 degrees below the horizon, at this location.)
```
