BeNomad map resources
======================

These map resources ship with the sample and are deployed by `Core.init(...)` into the app's
scoped external storage on first launch (the folder name is passed as `resourcesPathInAssets`,
see SdkConfig). The interactive map and POI markers will NOT render until these files are present.

Contents:

  day.cht            map graphic chart (required by MapStyleLoader.loadStyle)
  day.xml            companion style metadata for the day chart
  Fonts/             DejaVuSans*.ttf + FontMapping.cfg (required for map text)
  img/               POI + maneuver icons, incl. routestart.png / routestop.png
  vehicle.png        the vehicle/user symbol rendered by the Navigation engine

Optional:

  night.cht / night.xml   only if you want a dark map style

IMPORTANT — when you ADD or CHANGE files in this folder:
  Core.init only deploys this folder into the app's external files dir on a FIRST
  install or when the app's versionCode increases. If you change these assets after
  having already launched the app once, the new files will NOT be deployed and the
  map/fonts will fail to load. To force a fresh deployment, either:
    - clear the app data / reinstall (e.g. `adb shell pm clear com.benomad.sample`), or
    - bump `versionCode` in app/build.gradle.kts.
