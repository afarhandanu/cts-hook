# Lineage Pill CTS

Minimal LSPosed module for LineageOS/Pixel Launcher setups where native Circle to Search is already present, but long-pressing the gesture pill does not invoke it.

Current module version: `0.2.0`.

The module does not spoof device identity, does not clone Lens, and does not draw an overlay. It only tries to connect the existing navigation-handle long-press path to Android's native Contextual Search service.

## What It Hooks

- `com.google.android.apps.nexuslauncher` and `com.android.launcher3`
  - Enables the Launcher3 input-consumer gate before gesture handling begins.
  - Enables Launcher3/Quickstep's native `NavHandleLongPressHandler` entrypoint when that class exists.
  - Uses the launcher's checked invocation when available, then falls back directly to the native service if the launcher reports the feature unavailable.

- `com.android.systemui`
  - Adds a fallback long-click listener to known SystemUI navigation handle classes and the `home_handle` dispatcher.
  - Calls `ContextualSearchManager.startContextualSearch(ENTRYPOINT_LONG_PRESS_NAV_HANDLE)`.

- `android` / System Framework
  - Adds a narrow fallback permission bypass only for `startContextualSearch` callers whose UID belongs to SystemUI, Pixel Launcher, or Launcher3.
  - Other contextual-search permission checks and callers are left untouched.

## LSPosed Scope

Enable these scopes:

- System Framework / `android`
- System UI / `com.android.systemui`
- Pixel Launcher / `com.google.android.apps.nexuslauncher`
- Launcher3 / `com.android.launcher3` if your ROM uses it

Reboot after enabling the module. Restarting SystemUI and launcher may be enough, but rebooting is cleaner for the System Framework hook.

## Build

Open the project in Android Studio, or run:

```sh
gradle :app:assembleDebug
```

The project includes compile-only Xposed stubs, so it does not package Xposed API classes into the APK.

If you use a local Android SDK older than API 36, change `compileSdk` and `targetSdk` in `app/build.gradle` to `35`. The module runtime path itself only checks for Android 15+.

### Build on GitHub without Android Studio

Upload the complete project, including the hidden `.github` directory, to a
GitHub repository. Open **Actions**, select **Build APK**, then choose
**Run workflow**. When the run finishes, download the
`LineagePillCts-debug` artifact from the run summary.

## Install

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then enable the module and the scopes in LSPosed.

## Debug

```sh
adb logcat -s LineagePillCTS ContextualSearchInvoker NavHandleLongPressHandler ContextualSearchManager
```

Useful checks on device:

```sh
adb shell cmd package resolve-activity android.app.contextualsearch.action.LAUNCH_CONTEXTUAL_SEARCH
adb shell settings get secure search_all_entrypoints_enabled
```

If the Launcher3 hook logs as installed but long-press still does nothing, the ROM/launcher build may not include the Quickstep long-press input consumer. In that case this module falls back to the SystemUI `home_handle` listener, but some ROMs route gesture input before the handle view receives long-click events.

## AOSP References

- Android Contextual Search API: `ContextualSearchManager.startContextualSearch(int)` with `ENTRYPOINT_LONG_PRESS_NAV_HANDLE = 1`
- Contextual Search service starts only when `config_defaultContextualSearchPackageName` is configured
- Launcher3 Quickstep has `NavHandleLongPressHandler` for the navigation handle path on recent Android branches
