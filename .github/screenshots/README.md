# PR screenshots

Every PR that changes something an owner can see carries a Before/After table of real
screenshots (CLAUDE.md). The PNGs live here, one folder per branch, so the PR body can link
them — a body cannot render a file that is only on your disk.

## Why not just `adb screencap`

A local debug build has no RevenueCat key and no Supabase session. Driven by hand, the paywall
photographs *"Odo Pro isn't available to buy right now"* and the one-time sheet photographs its
empty state. Neither is the feature.

The instrumented harness seeds the owner and injects the store's answers, so a screenshot taken
from inside a test is what an owner with a real store sees. That is what `Screenshots.capture`
is for.

## Taking them

Write a test that navigates and captures — see `PaywallScreenshotTest` for the shape — then:

```sh
./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
adb install -r -g androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb install -r -g androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb shell am instrument -w -e class com.hopcape.odo.PaywallScreenshotTest \
  com.hopcape.odo.debug.test/com.hopcape.odo.OdoTestRunner
adb pull /sdcard/Android/data/com.hopcape.odo.debug/files/screenshots/ .
```

**`am instrument`, not `./gradlew connectedAndroidTest`.** Gradle uninstalls the app when the
run finishes, and the app's external files directory — where the captures are written — goes
with it. The tests pass, and the files are gone before you can pull them.

Downscale before committing (`sips -Z 700 in.png --out out.png`); a full-resolution phone
screenshot is ~300 KB and renders no better in a PR.

## Taking a "Before"

The Before is the same screen built from the base branch, so it needs a second checkout:

```sh
git worktree add --detach /tmp/before origin/develop
cp local.properties /tmp/before/
cp androidApp/google-services.json androidApp/src/debug/google-services.json \
   androidApp/src/stage/google-services.json  # into the matching paths under /tmp/before
```

Both are gitignored, and without them the build either fails on
`processDebugGoogleServices` or the app dies at startup with *"Default FirebaseApp is not
initialized"* — which reads like a product bug and is not one.

Then add the capture helper and a throwaway test to that worktree, and run the same commands.
`git worktree remove /tmp/before` when done.

## Linking them

Reference the **commit SHA**, not the branch:

```
https://raw.githubusercontent.com/Aumaidkh/Odo-Mobile/<sha>/.github/screenshots/<branch>/<file>.png
```

A branch URL breaks the moment the branch is deleted after merge, which is exactly when someone
reads the PR to find out what changed.
