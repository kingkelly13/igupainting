# How Launcher3's app search works, and Strata's rebuild

This report follows the "trace a feature, rebuild it, prove it" workflow. The target is
the All Apps search in AOSP Launcher3, the open-source home screen that Pixel and many
other Android launchers are built on. The rebuild is Strata's drawer search.

## 1. Executive summary

| | |
| --- | --- |
| Feature | Typing in the All Apps search box and getting matching apps |
| Verdict | Traced to code level. Rebuilt in Kotlin (`data/AppSearch.kt`) and wired into Strata's drawer. 14 unit tests pass. The side-by-side run against the upstream code is written but has **not been run yet** (see section 6). |
| Effort | One session: tracing about 8 files, a 150-line rebuild, tests and a diff tool |
| What changes for you | Search ranks apps whose label starts with your query first, then apps where your query starts a later word ("maps" → Google Maps, "tube" → YouTube), then apps that only contain it ("rome" → Chrome). Case and accents no longer matter ("cafe" finds "Café"). Every app the old search found is still found. |

How it works, in one line: **Launcher3 matches your query against the start of each word in an
app's label, using the locale's collation rules so case and accents are ignored. A word starts
after a space, at a capital in camelCase, at a digit or at a symbol.**

## 2. Target profile

| | |
| --- | --- |
| Target | AOSP Launcher3 All Apps search (`DefaultAppSearchAlgorithm`) |
| Type | Open-source Android app, Java and Kotlin source |
| Licence | Apache 2.0. Each traced file carries the AOSP Apache 2.0 header. Reading it and building something similar is permitted. |
| Source used | The Launcher3 code carried in [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) `16-dev`, commit `85493b9776d678c5b22d88b644f1ca530c3af92d` (3 Nov 2026). This session's network blocks `android.googlesource.com`, so this is a mirror, not AOSP itself (see section 8). |
| Provider | Source reading. REA (`rea-agents`) is not installed: this session's permission policy blocked installing it. REA is built for binaries without source, and since this target ships its source, reading the source gives better evidence than decompiled pseudocode would. |
| Why this target | Strata's drawer search was a plain substring filter. Launcher3's search is the reference behaviour most Android users know, and its licence allows building on it. |

## 3. Feature trace

From keystroke to results on screen. Paths are relative to Launcher3's `src/com/android/launcher3/`.

```
User types in the All Apps search field (ExtendedEditText)
 │
 ├─ allapps/search/AppsSearchContainerLayout.java:131  initializeSearch()
 │     wires the field to new DefaultAppSearchAlgorithm(context, addNoResultsMessage = true)
 │
 ├─ allapps/search/AllAppsSearchBarController.java:65   mInput.addTextChangedListener(this)
 ├─ allapps/search/AllAppsSearchBarController.java:98   afterTextChanged(s)
 │     mQuery = Utilities.trim(s)                         (Utilities.java:120, :588)
 │     empty  → mCallback.clearSearchResult()             (back to the full list)
 │     else   → mSearchAlgorithm.cancel(false); doSearch(mQuery, mTextConversions, mCallback)
 │
 ├─ search/SearchAlgorithm.java:33   default doSearch(query, suggestions, cb) → doSearch(query, cb)
 │     (the default algorithm ignores keyboard suggestions)
 │
 ├─ allapps/search/DefaultAppSearchAlgorithm.java:65   doSearch()
 │     runs on the model thread: getTitleMatchResult(apps.data, query)
 │     posts the result to the main thread → callback.onSearchResult(query, result)
 │
 ├─ allapps/search/DefaultAppSearchAlgorithm.java:88   getTitleMatchResult(apps, query)
 │     queryTextLower = query.toLowerCase()               (default locale)
 │     matcher = StringMatcher.getInstance()              (new Collator per search)
 │     walks apps in model order and stops at MAX_RESULTS_COUNT = 5   (:41)
 │     keeps app if StringMatcherUtility.matches(queryTextLower, info.title, matcher)
 │
 ├─ search/StringMatcherUtility.java:39   matches(query, target, matcher)    ← the core rule
 │     :44   false if query is empty or longer than the label
 │     :48   query contains a Han character → target.toLowerCase().contains(query)
 │     :57   for each index i where the query could still fit:
 │              isBreak(type[i], type[i-1], type[i+1])  (:159)
 │              and matcher.matches(query, target[i, i + query.length))   (:125)
 │
 ├─ allapps/search/AppsSearchContainerLayout.java:168   onSearchResult() → mAppsView.setSearchResults(items)
 ├─ allapps/ActivityAllAppsContainerView.java:443       setSearchResults() → animate to the search state
 └─ allapps/AlphabeticalAppsList.java:223               setSearchResults(): stores results in the order given

Pressing Enter or the keyboard's search key:
 allapps/search/AllAppsSearchBarController.java:118  onEditorAction() → launchHighlightedItem()
 allapps/search/DefaultSearchAdapterProvider.java:63 launches the highlighted (first) result
```

## 4. Evidence log

Confidence: **proven** means the code says so directly. **Likely** means strongly implied but
not fully traced. **Guessed** means inferred without direct evidence.

| # | Finding | Evidence | Confidence |
| --- | --- | --- | --- |
| E1 | Matching is a prefix test at word starts, not a substring test. | `StringMatcherUtility.java:57-66`: the loop only compares at indexes where `isBreak` is true. Upstream comment `:157`: "YouTube" matches "you" and "tube", but not "out". | Proven |
| E2 | Word-start rules. A word starts at: the first character; anything after a space, line or paragraph separator; a capital after a non-capital or before a lower-case letter; a lower-case letter after a non-letter; the first digit of a number; any math, currency, dash or other punctuation character. | `isBreak`, `StringMatcherUtility.java:159-199`. | Proven |
| E3 | All-caps runs: a capital followed by another capital, a digit, a symbol such as ® or the end of the label only starts a word if the previous character is not a capital. So "HTMLViewer" has word starts at H and V, and "LEGO®Builder" at L and B. | `:171-179`, plus the upstream examples at `:71-75`. | Proven |
| E4 | Case and accents are ignored. A `Collator` at PRIMARY strength with canonical decomposition compares the query with a window of the label of the same length. | `:117-119`, `:125-142`. | Proven |
| E5 | The prefix test: a window counts as a match if it is equal at primary strength, or if appending U+FFFF to the query makes the query sort at or after the window. | `:130-139` and its comment. | Proven |
| E6 | A query that contains any Han character uses a case-insensitive `contains` instead, because Chinese has no spaces between words. | `requestSimpleFuzzySearch`, `:225-236`, called at `:48`. | Proven |
| E7 | The query is trimmed of leading and trailing horizontal whitespace, then lowercased with the default locale. | `AllAppsSearchBarController.java:99`, `Utilities.java:120,588-593`, `DefaultAppSearchAlgorithm.java:92`. | Proven |
| E8 | No ranking: results come out in the model's list order, and only the first 5 are kept. | `DefaultAppSearchAlgorithm.java:41,98-104`. `AllAppsList.data` is an `ArrayList` filled in load order (`AllAppsList.java:79,182`). `AlphabeticalAppsList.setSearchResults` keeps the given order (`:223-233`). | Proven |
| E9 | The label searched is the activity's label, trimmed. | `AllAppsList.java:175` (`activityInfo.getLabel()`), `IconCache.java:659`, `AppInfo.java:115`. The icon cache's `CacheEntry.title` comes from a library outside the traced files. | Likely |
| E10 | Enter launches the first result. | `AllAppsSearchBarController.java:118-131`, `DefaultSearchAdapterProvider.java:63-71`. That the highlighted item is always the first result is not traced. | Likely |
| E11 | Search runs off the main thread and results are posted back. A new keystroke does not interrupt a running search (`cancel(false)`). | `DefaultAppSearchAlgorithm.java:58-73`, `AllAppsSearchBarController.java:103`. | Proven |

Data formats and storage: none. Search reads the in-memory app list only. It writes nothing
to disk and makes no network calls (`DefaultAppSearchAlgorithm` has no I/O).

Edge cases seen in the code:
- Labels are walked one UTF-16 unit at a time. At the second half of a surrogate pair
  (most emoji), `Character.getType` returns `SURROGATE`, which never starts a word.
- An empty query never matches, and neither does a query longer than the label.
- Lowercasing uses the default locale, so in Turkish "I" becomes "ı".

## 5. Reconstruction

New file `app/src/main/java/com/strataos/launcher/data/AppSearch.kt`, in pure Kotlin with no
Android imports, so it runs in plain JVM unit tests.

| Launcher3 behaviour | Strata |
| --- | --- |
| Word-start rule (E1-E3) | `startsWord(prev, cur, next)`, written as a `when` over category sets. The types are computed once per label instead of rolling three variables. |
| Collator prefix test (E4, E5) | `isPrefixAtPrimaryStrength`, same Collator settings, using the device locale |
| Han fallback (E6) | `hasHan()` → `indexOf` on the lowercased label |
| Lowercase with the default locale (E7) | `lowercase(locale)`, where `locale` defaults to `Locale.getDefault()` |
| Trim (E7) | The drawer already calls `query.trim()`. Kotlin's trim covers all Unicode whitespace, a slightly wider set than `\h`. |
| First 5 results in model order (E8) | **Different on purpose.** Strata's drawer shows a full grid, so there is no cap. Results are ranked `LabelStart`, then `WordStart`, then `Inside`, and keep alphabetical order within each group. |
| Substring matches are dropped | **Different on purpose.** Strata keeps them as `Inside`, ranked last, so nothing the old search found disappears. |
| Enter launches the first result (E10) | Unchanged. Strata already did this. |
| Runs on a worker thread (E11) | Not copied. With a phone's worth of apps, filtering takes well under a millisecond, so it stays inside `remember` on the main thread. |

`AppDrawer.kt` now calls `search.filter(trimmed, apps) { it.label }` in place of
`contains` plus a `startsWith` sort.

## 6. Verification results

**Unit tests:** `app/src/test/java/com/strataos/launcher/data/AppSearchTest.kt`, 14 tests, all
passing on the JVM (JDK 21, `Locale.US`). CI now runs `testDebugUnitTest` before it builds
the APK. They cover:

- Launcher3's own documented examples: "YouTube", "t-mobile", "Agar.io" and "LEGO®Builder"
- camelCase, spaces, caps runs, digits and symbols
- accents, both precomposed and decomposed; Han queries; emoji; empty and over-long queries
- Strata's ranking and order within each group
- a check that every app the old substring search found is still found

**Differential test (written, not yet run):** `tools/launcher3-diff` compiles the unmodified
upstream `StringMatcherUtility.java` next to Strata's `AppSearch.kt`. It then runs both on
every pairing of 170 labels with thousands of queries cut from them, in 8 locales, and fails on
any disagreement. This session's permission policy blocks running third-party code, so it has
only been type-checked. See `tools/launcher3-diff/README.md` to run it. Until it has run, the
claim that "the word-start rule is identical to Launcher3's" rests on a line-by-line reading
of the code above (E1-E7), so it is **likely, not proven**.

Sample cases. The Launcher3 column is worked out from rules E1-E6. The Strata and old-Strata
columns are real output from `AppSearch` on the JVM.

| Query | Label | Launcher3 | Strata | Old Strata (substring) |
| --- | --- | --- | --- | --- |
| `you` | YouTube | match | LabelStart | match |
| `tube` | YouTube | match | WordStart | match |
| `out` | YouTube | no | Inside | match |
| `maps` | Google Maps | match | WordStart | match |
| `rome` | Chrome | no | Inside | match |
| `mail` | Gmail | no | Inside | match |
| `viewer` | HTMLViewer | match | WordStart | match |
| `tml` | HTMLViewer | no | Inside | match |
| `cafe` | Café | match | LabelStart | **no** |
| `信` | 微信 | match | WordStart | match |

## 7. Build plan

1. Done: `AppSearch.kt`, the drawer wiring, the unit tests, the CI test step and the diff tool.
2. Run `tools/launcher3-diff` once (one command, see its README). Section 6 becomes proven if it
   reports `mismatches=0`.
3. Install the CI build and try searches on the phone, especially labels in your own language.
4. Optional: use `AppSearch` for the settings shortcuts in the drawer too. They still use
   "every word is contained".

## 8. Unknowns and limits

- **Collation on the phone.** Android's `java.text.Collator` is backed by ICU. The JDK's is
  not. Both sides of the differential test use the JDK's, so the test proves the algorithms are
  the same, not that every exotic character collates the same on the phone. Common Latin
  accents behave the same in both. Some locales go further on purpose (in Swedish, "ü" and
  "y" may count as the same letter).
- **AOSP versus the mirror.** The source came from Lawnchair's copy of Launcher3. The traced
  files carry unmodified AOSP headers, but I could not diff them against
  `android.googlesource.com` from this session. If you run the diff tool against an AOSP checkout,
  this gap closes.
- **Pixel and HyperOS launchers are not covered.** Pixel Launcher adds a closed-source search
  layer on top, and HyperOS has its own launcher. This trace covers only the open-source default.
- **Keyboard suggestions.** Launcher3 passes keyboard text conversions (`SuggestionSpan`) to
  the search, and the default algorithm ignores them. Strata ignores them too.
- **What would change my mind:** any `mismatches` line from the diff tool, or a newer Launcher3
  where `isBreak` or `requestSimpleFuzzySearch` has changed. Re-run the tool after Launcher3
  updates.
