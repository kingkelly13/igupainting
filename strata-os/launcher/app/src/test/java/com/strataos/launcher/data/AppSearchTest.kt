package com.strataos.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Behaviour of the drawer search. The word-start cases come from the examples documented in
 * AOSP Launcher3's StringMatcherUtility and from reading its rules (see
 * docs/app-search-trace.md). The ranking and Inside cases are Strata's own.
 */
class AppSearchTest {
    private val search = AppSearch(Locale.US)

    private fun assertWordStart(query: String, label: String) =
        assertTrue("\"$query\" should match a word start in \"$label\"", search.matchesWordStart(query, label))

    private fun assertNotWordStart(query: String, label: String) =
        assertFalse("\"$query\" should not match a word start in \"$label\"", search.matchesWordStart(query, label))

    // ---- Word starts (Launcher3 behaviour) -----------------------------------------------

    @Test
    fun camelCaseStartsAWord() {
        // Launcher3's own example: "YouTube" matches "you" and "tube", but not "out".
        assertWordStart("you", "YouTube")
        assertWordStart("tube", "YouTube")
        assertNotWordStart("out", "YouTube")
        assertWordStart("app", "WhatsApp")
    }

    @Test
    fun spacesStartWords() {
        assertWordStart("maps", "Google Maps")
        assertWordStart("play", "Play Store")
        assertWordStart("store", "Play Store")
        assertWordStart("google maps", "Google Maps")
        assertNotWordStart("maps google", "Google Maps")
    }

    @Test
    fun aWordOnlyMatchesFromItsStart() {
        assertNotWordStart("rome", "Chrome")
        assertNotWordStart("mail", "Gmail")
        assertNotWordStart("ogle", "Google")
    }

    @Test
    fun symbolsAndDigitsStartWords() {
        // Launcher3's examples: "t-mobile", "Agar.io" and "LEGO®Builder".
        assertWordStart("mobile", "t-mobile")
        assertWordStart("io", "Agar.io")
        assertWordStart("builder", "LEGO®Builder")
        assertWordStart("23", "FC 23")
        assertWordStart("2", "Fit2")
        assertNotWordStart("3", "FC 23")
    }

    @Test
    fun capsRunsOnlyBreakBeforeALowerCaseLetter() {
        assertWordStart("viewer", "HTMLViewer")
        assertWordStart("html", "HTMLViewer")
        assertNotWordStart("tml", "HTMLViewer")
        assertNotWordStart("ego", "LEGO®Builder")
    }

    @Test
    fun caseAndAccentsAreIgnored() {
        assertWordStart("CAM", "Camera")
        assertWordStart("cafe", "Café")
        assertWordStart("café", "Cafe")
        assertWordStart("muller", "Müller Shop")
        assertWordStart("cafe", "Café Bar") // decomposed é
    }

    @Test
    fun hanQueriesMatchAnywhere() {
        assertWordStart("信", "微信")
        assertWordStart("微", "微信")
        assertNotWordStart("支", "微信")
    }

    @Test
    fun emptyOrTooLongQueriesNeverMatch() {
        assertNotWordStart("", "Camera")
        assertNotWordStart("cameras", "Camera")
        assertNull(search.match("", "Camera"))
    }

    @Test
    fun emojiInLabelsDoNotBreakMatching() {
        assertWordStart("party", "🎉 Party")
        assertWordStart("🎉", "🎉 Party")
        assertNotWordStart("arty", "🎉 Party")
    }

    // ---- Strata's ranking ----------------------------------------------------------------

    @Test
    fun matchKinds() {
        assertEquals(MatchKind.LabelStart, search.match("cam", "Camera"))
        assertEquals(MatchKind.WordStart, search.match("maps", "Google Maps"))
        assertEquals(MatchKind.Inside, search.match("rome", "Chrome"))
        assertNull(search.match("xyz", "Chrome"))
    }

    @Test
    fun filterRanksLabelStartThenWordStartThenInside() {
        val apps = listOf("Calculator", "Camera", "Google Chrome", "Music", "Settings")
        assertEquals(
            listOf("Calculator", "Camera", "Google Chrome", "Music"),
            search.filter("c", apps) { it },
        )
    }

    @Test
    fun filterKeepsInputOrderWithinAKind() {
        val apps = listOf("Mail", "Maps", "Google Maps", "Gmail", "Messages")
        assertEquals(listOf("Mail", "Maps", "Messages", "Google Maps", "Gmail"), search.filter("m", apps) { it })
    }

    @Test
    fun filterFindsEverythingTheOldSubstringSearchFound() {
        val apps = listOf("Chrome", "Gmail", "YouTube", "Google Maps", "Photos", "Clock", "Contacts")
        for (query in listOf("o", "om", "rome", "mail", "tube", "ts", "oc")) {
            val old = apps.filter { it.contains(query, ignoreCase = true) }.toSet()
            val now = search.filter(query, apps) { it }.toSet()
            assertTrue("\"$query\": $old should all be in $now", now.containsAll(old))
        }
    }

    @Test
    fun filterTrimsAndIgnoresBlankQueries() {
        val apps = listOf("Camera", "Clock")
        assertEquals(listOf("Camera"), search.filter("  cam ", apps) { it })
        assertEquals(emptyList<String>(), search.filter("   ", apps) { it })
    }
}
