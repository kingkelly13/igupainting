# launcher3-diff

Checks that Strata's drawer search (`app/src/main/java/com/strataos/launcher/data/AppSearch.kt`)
decides word-start matches exactly as AOSP Launcher3's `StringMatcherUtility` does.

It compiles the upstream Java file unmodified next to Strata's Kotlin file. Two small stand-ins
take the place of Android types: `TextUtils.indexOf` and `@Nullable`. It then runs both
matchers on every pairing of 170 labels with thousands of queries, in 8 locales. It fails if
they disagree anywhere and lists up to 50 of the disagreements. This is a JVM-only tool. It is
not part of the app build, and CI doesn't run it.

## Run it

You need JDK 17 or newer and a Launcher3 source checkout. The AOSP original:

```sh
git clone --depth 1 https://android.googlesource.com/platform/packages/apps/Launcher3
```

Or, from a network that can't reach googlesource, the Lawnchair copy this trace used:

```sh
git clone --depth 1 --filter=blob:none --sparse https://github.com/LawnchairLauncher/lawnchair
git -C lawnchair sparse-checkout set --no-cone \
  /src/com/android/launcher3/search/ /src/com/android/launcher3/util/IntArray.java
```

Then, from `strata-os/launcher`:

```sh
./gradlew -p tools/launcher3-diff test -Plauncher3Src=/path/to/Launcher3/src
```

`-Plauncher3Src` must point at the `src` folder that contains `com/android/launcher3/`.

The tool writes a summary to `tools/launcher3-diff/build/differential-report.txt`: pairs per locale,
how many Launcher3 counts as matches, and `mismatches=0` when the two agree everywhere. It also
writes a side-by-side sample table to `build/samples.md`.

Re-run it when Launcher3 changes `StringMatcherUtility`, and before you change `AppSearch`'s
word-start rules.
