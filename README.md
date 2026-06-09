# BeNomad mSDK — Android Navigation Sample

A complete, heavily-documented Android app showing how to build an end-to-end **turn-by-turn
navigation** experience with the **BeNomad mobile SDK (mSDK)**. It is meant to be read end to end and
adapted: every SDK touch-point is in the open, the non-obvious SDK behaviours are documented at the
call site, and the UI is built to be re-themed and re-laid-out without forking any component.

Built from scratch as a single Gradle module, with **manual dependency injection** (no Hilt) and
**Jetpack Compose + Material 3**. The only third-party dependency is the mSDK itself.

This README has two parts:

- **[Part 1 — Functional](#part-1--functional):** what the app does, and how you configure and
  customise it (license, map data, vehicle profiles, UI).
- **[Part 2 — Technical](#part-2--technical):** how it is implemented — architecture, the exact mSDK
  call sequences, how the UI customisation works, and the **SDK traps** the sample works around.

---

## Contents

- [Getting started](#getting-started)
- [Part 1 — Functional](#part-1--functional)
  - [What the app does](#what-the-app-does)
  - [Feature tour](#feature-tour)
  - [Configuring the license](#configuring-the-license)
  - [Map data: full vs hybrid](#map-data-full-vs-hybrid)
  - [Vehicle profiles](#vehicle-profiles)
  - [How much the UI can be customised](#how-much-the-ui-can-be-customised)
  - [What is out of scope (your remaining work)](#what-is-out-of-scope-your-remaining-work)
- [Part 2 — Technical](#part-2--technical)
  - [Architecture](#architecture)
  - [Project layout](#project-layout)
  - [Key mSDK call sequences](#key-msdk-call-sequences)
  - [Customising the UI (technical)](#customising-the-ui-technical)
  - [What to watch out for](#what-to-watch-out-for)

---

## Getting started

### Prerequisites

- Android Studio (Ladybug or newer) and JDK 17.
- A BeNomad **license** (a *purchase UUID*).
- **Nexus** Maven credentials for the BeNomad artifact repository.

### Credentials — `local.properties`

Copy `local.properties.sample` to `local.properties` (git-ignored) and fill in:

| Key | Purpose |
|---|---|
| `NEXUS_URL` | BeNomad Nexus Maven URL (where the `com.benomad.sdk:*` artifacts resolve from). |
| `NEXUS_USERNAME`, `NEXUS_PASSWORD` | Nexus credentials. |
| `MSDK_PURCHASE_UUID` *(optional)* | A developer default that pre-fills the in-app license screen so you never retype it. Not required to build. |


### Map resources (included)

The map resources ship with this repo at `app/src/main/assets/benomad_resources/` (the day map style
`day.cht`/`day.xml`, `Fonts/`, the POI/maneuver icons in `img/`, and `vehicle.png`) — there is nothing
to download or place yourself. `Core.init` deploys them into the app's external files directory on
first launch (it only re-deploys on a fresh install or a `versionCode` bump; see
[Troubleshooting](#troubleshooting)).

### Build & run

Build and run on a device or emulator that has a **TTS engine** installed (for voice guidance). The
SDK is **not** initialized at app startup — initialization happens on the splash screen once you have
entered a license and granted location permission.

### Build configuration

| | |
|---|---|
| AGP | 8.13 |
| Gradle | 8.13 |
| Kotlin | 2.2.20 (Compose compiler plugin bundled with Kotlin 2.x) |
| Java / JVM target | 17 *(required by the mSDK)* |
| compileSdk / targetSdk | 35 |
| minSdk | 23 |
| Core-library desugaring | 2.1.5 — **mandatory** (`isCoreLibraryDesugaringEnabled = true`) |

The mSDK version is set **once** in `gradle/libs.versions.toml` (the `msdk` key). The coordinate
depends on the publish channel your Nexus hosts (release / beta / develop), so set it to whatever is
available to you rather than copying a literal. The app declares **every** mSDK module it references,
because the SDK's inter-module dependencies are `implementation` (not transitive):

```
com.benomad.sdk:error-manager     // mandatory base
com.benomad.sdk:core              // init, license, map data, GeoPoint, DynamicLayersGroup
com.benomad.sdk:maps              // MapView, MapStyleLoader, POIStyle
com.benomad.sdk:geocoder          // OnlineGeoCoder, Address, GeoDecoder
com.benomad.sdk:gps-manager       // GPSManager, LocationFromBuiltInGPS, LocationFromRoute
com.benomad.sdk:vehicle-manager   // Profile, Vehicle, VehicleModel
com.benomad.sdk:planner           // Planner, RoutePlan, Route, SymbolicManeuverStyle
com.benomad.sdk:navigation        // Navigation, listeners, InstructionsIconsStyles, foreground service
```

### Troubleshooting

- **`loadStyle failed` / `LoadFontMapping failed` during init.** `Core.init` deploys
  `assets/benomad_resources/` into the app's external files directory **only on first install or when
  `versionCode` increases**. If you change those assets after launching once, they are not
  re-deployed and the chart/fonts go missing. Fix: clear app data
  (`adb shell pm clear com.benomad.sample`) or bump `versionCode`, then relaunch.
- **`UnsatisfiedLinkError` on the first SDK call.** Verify the mSDK AARs include your test device's
  ABI.
- **No voice guidance.** The device needs an installed TTS engine; the sample only enables voice when
  `Navigation.isTTSAvailable()` is true.
- **Empty search results / init license error.** Search needs an autocomplete-enabled license and a
  network connection; set the map bias / default region (`SdkConfig.DEFAULT_MAP_CENTER`, default
  Paris) to a city inside your licensed coverage.

---

## Part 1 — Functional

### What the app does

A single-Activity Compose app that walks from first launch to live turn-by-turn guidance:

```
Consent → License entry → Location permission → [Notification permission (Android 13+)]
  → Splash (SDK init + map data) → Map → Route preview → Guidance
```

Onboarding is **resumable**: after each step the next destination is recomputed from persisted
preferences plus *live* permission / init state, so already-satisfied steps are skipped on the next
launch. Reaching the **Map** screen means the SDK is initialized and the map style is loaded.

### Feature tour

**Onboarding & initialization**
- **Consent** — a one-time terms screen (placeholder text you replace with your own agreement).
- **License entry** — you paste your BeNomad purchase UUID (see [Configuring the license](#configuring-the-license)).
- **Permissions** — location is requested with the AndroidX launcher (with an "open settings"
  fallback when permanently denied); the Android 13+ notification permission is requested
  **non-blocking** (skipping it still lets you navigate, you just lose the guidance notification).
- **Splash** — runs SDK initialization, shows first-launch map-download/extraction progress (full
  maps) or a spinner, and surfaces a retry on failure.

**Map**
- The interactive mSDK `MapView` embedded in Compose, with built-in **pan / pinch-zoom / rotate /
  tilt** and on-screen zoom buttons.
- Opens centered on your last known position and **auto-follows** you (a 3D tracking camera). Panning
  drops follow mode; a recenter button reappears once you've panned away.
- **Address search** (online autocomplete, biased to your position); picking a result drops a
  destination marker and offers **Plan route**.
- A **streaming indicator** while hybrid map tiles load, and a hybrid-only **offline pre-cache** button.

**Route preview**
- Computes the route from your position to the destination for a selected **vehicle profile**
  (chips), with up to **two alternative routes**.
- Each route is a selectable card (duration / distance / arrival); the selected route is drawn on top,
  alternatives beneath. A per-route button opens the **full turn-by-turn list** in a bottom sheet.
- **Start** begins live guidance; **Start (demo)** simulates driving along the route. Layout is
  responsive (map-above-panel in portrait, map-beside-panel in landscape).

**Guidance**
- A full-screen map the engine drives: vehicle symbol + route + 3D follow camera, the driven-over part
  of the route erased.
- A **maneuver bar** (turn icon + distance-to-next + instruction), automatic **voice guidance** with a
  mute toggle, a **speed-limit** badge, and a **route-info bar** (remaining time / ETA / distance).
- An **arrived** card on reaching the destination; stop by button or back press. Guidance keeps
  running (with a foreground notification) if the app is backgrounded, and is torn down cleanly if the
  app is swiped away.

### Configuring the license

The license is a BeNomad **purchase UUID**. There are two ways to supply it:

1. **In-app** — paste it on the license-entry screen; it is trimmed and persisted, and reused on the
   next launch.
2. **Developer default** — set `MSDK_PURCHASE_UUID` in `local.properties`; it is compiled into
   `BuildConfig` and pre-fills the entry field so you never retype it during development.

The entered value wins; otherwise the build-time default is used. The UUID is passed to `Core.init`
on the splash screen — that single call performs the license / LBO check, which also **decides
whether your map data is full or hybrid** (see below).

> The license field is seeded once (via `rememberSaveable`), so changing `MSDK_PURCHASE_UUID` only
> re-prefills the field after you clear app data. The field stays editable regardless.

### Map data: full vs hybrid

**You do not choose** full vs hybrid — the license / LBO decides it inside `Core.init`, and the app
only *observes* the result (`Core.isUsingHybridMaps()`).

- **Full** — the whole map is downloaded and deployed; the app works offline. On first launch you see
  download then extraction progress; thereafter newer base maps are auto-accepted for the next launch.
- **Hybrid** — only part of the map is on disk and the rest **streams** on demand as you pan (shown by
  a small streaming chip). On a hybrid license, the map screen also shows an **offline pre-cache**
  button that downloads a whole country (the sample uses `FRA`) for offline use via
  `loadFullMapsInCountry`.

### Vehicle profiles

Routing profiles are defined in an editable asset — `app/src/main/assets/vehicle_profiles.json` — and
parsed into mSDK `Profile`/`Vehicle` objects at runtime. There is no settings UI; **edit the file and
rebuild** to add or change a profile. The route-preview screen lists the profiles as chips.

```json
{
  "profiles": [
    { "id": "car", "name": "Car", "transportationMode": "PASSENGER_CAR" },
    { "id": "light_truck", "name": "Light truck", "transportationMode": "DELIVERY_TRUCK",
      "heightM": 2.0, "lengthM": 5.0, "widthM": 2.0, "weightT": 4.5, "weightPerAxleT": 2.5,
      "nbTrailers": 0, "maxSpeedKmh": 120.0, "majorRoads": true },
    { "id": "pedestrian", "name": "Pedestrian", "transportationMode": "PEDESTRIAN" }
  ]
}
```

- `transportationMode` is an mSDK enum name: `PASSENGER_CAR`, `DELIVERY_TRUCK`, `PEDESTRIAN`,
  `BICYCLE`, `TAXI`, `PUBLIC_BUS`, `MOTORCYCLE` (unknown values fall back to `PASSENGER_CAR`).
- **Units:** dimensions in **metres** (`heightM`/`lengthM`/`widthM`), weights in **metric tonnes**
  (`weightT`/`weightPerAxleT`), speed in **km/h** (`maxSpeedKmh`). `nbTrailers` is an integer
  (`0` = none; avoid `-1`, the SDK "unset" sentinel). `majorRoads: true` biases routing toward major
  roads.
- Truck dimensions/weights only take effect with a **truck mode** *and* a license that allows truck
  routing. Missing numeric fields default to `0.0`. A malformed entry is skipped (not fatal); if the
  file is unreadable the app falls back to a single Car profile.

### How much the UI can be customised

The sample is designed so a client can restyle it without forking any screen, through **two
mechanisms** (the technical how-to is in [Customising the UI (technical)](#customising-the-ui-technical)):

- **Theme (visuals).** Colours, type and the shared card style live in `ui/theme/`. Because the
  screens are built almost entirely from standard Material 3 components reading
  `MaterialTheme.colorScheme` / `typography`, **editing the theme files restyles the large majority of
  the UI** with no per-screen wiring. Light and dark themes are included and follow the system.
- **Layout (`modifier`).** Every screen and reusable component exposes a `modifier: Modifier =
  Modifier` applied to its root, so you can re-position, re-size, pad or wrap any of them from the call
  site without touching its internals.

A few elements are **intentionally not themed** because their meaning is fixed — the regulatory
speed-limit sign (white disc / red ring / black number), the pill-shaped profile chips, and the
circular control buttons. Leave those as-is when you re-brand.

### What is out of scope (your remaining work)

The sample deliberately covers a focused single-destination flow. The following are **not**
implemented and are the integrator's job to add — each is noted in code at the point it would slot in:

| Not implemented | Where to add it |
|---|---|
| **Off-route reroute** | The engine already reports an `OFF_ROUTE` state in the progress listener; recompute a route from the current position and restart/update the session. |
| **Waypoints / via points** | `RoutePlan` is built with an empty via-points list; populate it and handle `ArrivalListener.onViaPointReached`. |
| **Lane & signpost guidance** | `onNewInstruction` already delivers `signpost` and `laneInfo` bitmaps; the sample renders only the primary maneuver icon + text. |
| **Custom TTS voice control** | Voice is engine-driven; implement `onNewVocalInstruction` for bespoke text / voice / locale / a different engine. |
| **Night / dark map style** | A single day chart is loaded; add a night chart and switch `MapStyle` at runtime. |

Also out of scope (kept out to stay readable): EV/charging, Android Auto, traffic-sign / safety-camera
/ better-route alerts, favorites, a multi-stop "circuit" mode, and a settings UI.

---

## Part 2 — Technical

### Architecture

- **Single `:app` Gradle module**, organized by feature package under `com.benomad.sample` so every
  SDK touch-point is visible.
- **Manual DI.** A single `SdkProvider` (built once in `SampleApp.onCreate`, reachable via
  `(application as SampleApp).sdk`) owns the process-lifetime SDK objects that must outlive screens:
  `OnlineGeoCoder`, `Planner`/`RoutePlanner`, `NavigationController`, `GpsController`,
  `MapStyleProvider`, `MapDataController`, the marker/route renderers and the onboarding preferences.
  These are `lazy` because they may only be constructed **after** `Core.init` completes. ViewModels
  obtain the provider through a tiny `sdkViewModel(sdk) { … }` factory helper.
- **MVVM + StateFlow.** One ViewModel per screen exposing a single immutable UI-state via `StateFlow`
  (collected with `collectAsStateWithLifecycle`). Composables only render state and forward intent.
- **Controllers vs ViewModels — the key split.** SDK listener registration and **deterministic
  teardown** live in long-lived *controllers* held by `SdkProvider`, not in ViewModels. ViewModels
  create the listener objects and ask the controller to register/unregister them. This is what lets
  the SDK engine survive configuration changes while teardown stays explicit and symmetric.
- **Async bridging.** Callback APIs (`Core.init`, `Address.create`, `Planner.computeRoute`) are
  wrapped into `suspend` functions via `suspendCancellableCoroutine`, run off the main thread, with
  results posted back to Main.

### Project layout

```
com.benomad.sample
  SampleApp.kt            Application; builds & holds the SdkProvider (manual-DI root).
  MainActivity.kt         Single Activity; edge-to-edge; setContent { AppNavHost() }.
  AppNavHost.kt           NavHost wiring the screen flow. AppRoutes.kt — route constants.
  SdkViewModel.kt         sdkViewModel(sdk){…} factory helper (no DI framework).

  sdk/                    *** all direct mSDK plumbing — the heart of the example ***
    SdkProvider.kt        Lazy process-scoped SDK singletons + the route→guidance hand-off.
    SdkConfig.kt          Resources folder name, default center, dev default license.
    SdkInitializer.kt     Core.init wrapped as suspend (license-error observer first).
    MapDataController.kt  Map-download/streaming observers → MapDownloadProgress state.
    MapDownloadProgress.kt  Sealed progress model (Idle/Downloading/Extracting/StreamingHybrid/Ready/Failed).
    MapStyleProvider.kt   Loads the day chart; registers POI + polyline styles.
    GeoExt.kt             GeoPoint(lon,lat) helpers; distance/duration/ETA formatters.

  onboarding/             Consent, license entry, permission screens, splash + the router.
  map/                    MapScreen, SampleMapView (AndroidView host), MapViewModel,
                          DestinationMarkerManager.
  search/                 DestinationSearchBar, SearchViewModel, SearchState.
  route/                  RoutePreviewScreen, RoutePreviewViewModel/State, RoutePlanner,
                          RouteMapRenderer, VehicleProfileRepository.
  guidance/               NavigationController, GpsController, GuidanceLifecycleService,
                          GuidanceStyles, GuidanceScreen, GuidanceViewModel, GuidanceUiState.
  ui/theme/               Color.kt, Type.kt, Theme.kt, Style.kt.
  ui/components/          CircularIconButton.kt (shared control button).
```

### Key mSDK call sequences

**Initialization** (on the splash screen; nothing is initialized in `Application.onCreate`):
1. `MapDataController.registerDownloadObservers()` **before** `Core.init`, so a first-launch full-map
   download is visible.
2. `Core.init(appContext, purchaseUuid, licenseKey = null, getDefaultMapsPath(appContext, false),
   "benomad_resources", mapsPathInAssets = null, callback)` — runs native init off the main thread,
   deploys the bundled resources, performs the license/LBO call, and reports back on the main thread.
   It is **single-flight** (short-circuits when `Core.isInit()` is already true). A license failure
   arrives through a **separate** error callback that fires *before* the generic init error — register
   it first and prefer its message.
3. On success: `MapDataController.onCoreReady()` reads `isUsingHybridMaps()` and attaches the matching
   runtime observer (hybrid tile-streaming, or full-map update-available auto-accept).
4. `MapStyleProvider.load()` loads the day chart (off the main thread, idempotent) and registers the
   POI marker + route polyline styles with **absolute** icon paths.

**Search:** per keystroke, cancel the previous job and `delay(500)` (the SDK does not debounce), then
`OnlineGeoCoder.autoComplete(query, OnlineSearchFilter(language, position = bias), callback)`. The
callback arrives on the HTTP thread, so discard out-of-order responses and drop results without a
routable coordinate.

**Route computation** (`RoutePlanner`):
1. **Map-match both endpoints** with `Address.create(point, callback)` and route on the matched
   `Address.location` — raw `GeoPoint`s silently fail to route.
2. `Planner.computeRoute(RoutePlan(departures, destinations, viaPoints = [],
   RouteOptions(vehicle, maxAlternativeRoutes = 2)), listener)` — up to 3 routes total, primary first.
   The Planner is **single-flight**; on a fast re-submit it returns a transient `BUSY`, which the
   sample retries (bounded) after calling `planner.cancel()`.

**Guidance start** (`NavigationController.startGuidance`), with thread placement that matters:
1. Pick the source: real `LocationFromBuiltInGPS` or demo `LocationFromRoute`.
2. `GPSManager.start(source)` **on the main thread** (it registers a GNSS callback that needs a Looper).
3. Bind the source to the engine (`initLocationDataSource` / `changeLocationDataSource`) **off the main thread**.
4. **On the main thread:** `ttsMode`, `audioEnabled`, `hidePassedRoute(true)`, `zoomLevel3D = 16.0`,
   `setNavigationMapViewMode(GUIDANCE_VIEW_3D)`.
5. `Navigation.startSession(appContext, ForegroundNotificationContent(…), route,
   InstructionsIconsStyles(…))` **off the main thread** — passing the icon styles is what makes the
   engine deliver maneuver bitmaps in `onNewInstruction`.
6. Start the task-removal sentinel service.

**Guidance stop** (`stopGuidance`, awaited *before* navigating away): `stopSession` off the main
thread, then `GpsController.stop()` on the main thread (`stopSession` does **not** release the GPS
source), then stop the sentinel service.

**Map rendering:** markers and route polylines are `Form`s added to a `DynamicLayersGroup` under a
class id whose icon/style was registered at style-load. The group is held in a controller/ViewModel
(not the view), so it **survives `MapView` recreation** — on a fresh view it is simply re-attached and
its forms re-render. Selection changes *move* a polyline between the "selected" and "alternative"
classes rather than redrawing everything. See the form-ownership trap below.

### Customising the UI (technical)

**Theme** — `app/src/main/java/com/benomad/sample/ui/theme/`
- `Color.kt` — raw brand colour constants (e.g. `Blue40` light primary, `Blue80` dark primary, `Ink`
  surface/onSurface, `Dust` outline). **Change your brand colours here.**
- `Theme.kt` — `BeNomadSampleTheme` maps the palette into `lightColorScheme`/`darkColorScheme` and
  applies `MaterialTheme(colorScheme, typography)`. **Change which colour fills which role here**
  (e.g. `primary` = selected route card + selected profile chip + primary buttons; `surface` = the
  overlay cards; `outline` = card borders). To add Android-12+ dynamic colour, plug
  `dynamicLightColorScheme/dynamicDarkColorScheme` in here.
- `Type.kt` — `SampleTypography` copies the Material 3 default scale and overrides only the weight to
  `Light` (sizes/hierarchy preserved). **Change the font / weights / sizes here.**
- `Style.kt` — two shared overlay-card tokens, `SampleCardShape` (10 dp corners) and
  `SampleCardElevation` (8 dp shadow), used by the guidance bars and route cards. **Change the cards'
  silhouette/shadow here, once, for all of them.**

**Per-component layout & params**
- Every screen and component takes `modifier: Modifier = Modifier` applied to its root — pass a
  `Modifier` at the call site to re-position/re-size without forking. The guidance screen's portrait
  vs landscape layouts are produced purely by passing different modifiers to the *same* components
  (e.g. `Modifier.align(…)`, `widthIn(max = screenWidthDp / 3)`) — a worked example of re-laying-out
  without duplication.
- `ui/components/CircularIconButton.kt` (the shared control button) exposes `tint`, `shape`
  (`CircleShape`) and `elevation` (8 dp) parameters — re-skin all control buttons by editing the
  component, or one instance by passing params.
- The guidance bars (`NextInstructionBar`, `RouteInfoBar`) take `shape` / `shadowElevation` (and the
  route bar a `fillContentWidth` flag) so one composable renders either a flat bar or a rounded
  floating card.

**Intentionally not themed (semantic literals).** The speed-limit sign (`Color.White`/`Color.Red`/
black), the bold emphasis on route durations, the full-pill profile chips
(`RoundedCornerShape(50)`) and the circular control buttons (`CircleShape`) are hard-coded on
purpose — their look carries meaning, not brand. Leave them when re-theming.

**`MaterialTheme.shapes` is deliberately not overridden.** The card radius lives in `Style.kt` instead,
because overriding the theme's shapes would also move the default corners of every Material `Card`,
bottom sheet, etc. If you *want* that global behaviour, pass `shapes = Shapes(...)` to the
`MaterialTheme(...)` call in `Theme.kt` and have components read `MaterialTheme.shapes.*`.

### What to watch out for

The sample is shaped by a handful of mSDK behaviours that are easy to get wrong, in three groups:
**under-documented sharp edges** (the docs don't make the behaviour or its fix clear — where this
sample saves the most time), **documented API notes** (stated in the docs, but easy to trip over),
and **our own integration choices** (decisions the sample made that the SDK does not mandate). Each
is also explained in code at the file shown.

#### Under-documented sharp edges

| Behaviour | How the sample handles it |
|---|---|
| **A non-null `Error` with `code == 0` can mean success.** The KDoc documents only "an `Error` or `null`" and offers no `isSuccess()`/`NO_ERROR` helper, yet `initLocationDataSource` / `changeLocationDataSource` / `detachMapView` can return a non-null `Error` of `code 0` on success (the SDK's own code guards these with `code != 0L`). | Treat success as `error == null || error.code == 0L` on those calls. `guidance/GpsController.kt`, `NavigationController.kt` |
| **Map-matching is required before `Planner.computeRoute` — but nothing on `computeRoute`/`RoutePlan` says so.** `RoutePlan` accepts raw `GeoPoint`s; a raw point has no road location, so `computeRoute` throws inside its background coroutine and **fires no listener callback** — the call silently drops. | Snap both endpoints via `Address.create` first and route on the matched `Address.location`. `route/RoutePlanner.kt` |
| **The Planner is single-flight; a fast re-compute hits a transient `BUSY`.** Single-flight and `BUSY` aren't in the published API reference (the error enum is excluded from the generated docs), and `cancel()`'s timing isn't documented. | Cancel the prior computation and retry on `BUSY` (bounded: 3× / 150 ms). `route/RoutePlanner.kt` |
| **GPS `start()` must run on a thread with a Looper.** `LocationFromBuiltInGPS.start()` registers a GNSS/location callback (`requestLocationUpdates` / `registerGnssStatusCallback`) bound to the caller's Looper — Android behaviour the SDK KDoc doesn't mention. | Call `GPSManager.start()` / `stop()` on the main thread. `guidance/GpsController.kt` |
| **Rotating during a guidance session can crash natively** (observed, undocumented): a race between the engine's guidance thread and the GL view's destruction (a SIGSEGV in `CBHLGuidance::UpdateView`). | Declare a broad `android:configChanges` so the Activity + MapView survive rotation in place; re-apply the vehicle on-screen position on resize (the engine stores it as pixels derived from the view size). `AndroidManifest.xml`, `guidance/NavigationController.kt` |
| **The SDK foreground/location service does not stop the guidance *session* on app-swipe.** Its `onTaskRemoved` only drops its own notification (`stopSelf()`); the native session, progress thread, TTS and demo movement keep running headless. Undocumented. | A small sentinel service (`android:stopWithTask="false"`) stops the session + GPS source in `onTaskRemoved`. `guidance/GuidanceLifecycleService.kt` |

#### Documented API notes (easy to get wrong)

| Note | Detail |
|---|---|
| **`GeoPoint(lon, lat)` is longitude-first.** Documented (`@param lon` / `@param lat`), but the inverse of the common `(lat, lng)` convention, and the SDK does no range validation — a swapped pair lands silently elsewhere. | Build every point through `geoPoint(longitude, latitude)` / `Location.toGeoPoint()`. `sdk/GeoExt.kt` |
| **`Navigation.getInstance(appContext)` initializes the engine only when a context is passed** (documented in its KDoc). | Pass the context once to initialize; fetch the engine afterwards with the no-arg `Navigation.getInstance()`, which returns the same singleton without re-initializing. `guidance/NavigationController.kt` |
| **`DynamicLayersGroup` form ownership** (documented on `addForm` / `remove`): a `Form` belongs to one group/classID at a time; `remove(classID, form, …)` detaches it (stays re-addable) while `remove(classID)` destroys every form in that class. | Track each form's current class and detach from that exact class before re-adding. `route/RouteMapRenderer.kt`, `map/DestinationMarkerManager.kt` |
| **Navigation attaches only one `MapView` at a time** (documented on `attachMapView`): attaching a new view replaces and detaches the previous one, and a destroyed view auto-detaches via `onMapDestroyed`. | `guidance/NavigationController.kt` |

#### Our integration choices (not SDK requirements)

- **Heavy session calls run off the main thread.** `startSession` / `stopSession` carry no threading contract in the SDK; the sample dispatches them off-main only to avoid blocking the UI (they do native + foreground-service work). `guidance/NavigationController.kt`
- **No `detachMapView` / session-stop in a Compose `onDispose`.** `onDispose` also fires on a configuration change and during the screen-exit transition, where it would race the next screen's re-attach. The sample tears down only on explicit exit (Stop / back). `guidance/GuidanceScreen.kt`

#### One deliberate display choice

The maneuver distance is shown in coarse steps (nearest 5 m under 100 m, nearest 10 m under ~1 km,
then kilometres) so it doesn't flicker every metre. For whole kilometres the value is **rounded up on
purpose** (`(km + 0.5).roundToInt()`) so the figure is never lower than the real remaining distance —
this is intentional, not a rounding bug. `sdk/GeoExt.kt`
