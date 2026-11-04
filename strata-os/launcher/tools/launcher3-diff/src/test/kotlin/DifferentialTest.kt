import com.android.launcher3.search.StringMatcherUtility
import com.strataos.launcher.data.AppSearch
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * Runs the unmodified AOSP Launcher3 matcher and Strata's AppSearch on the same inputs and
 * fails on any disagreement.
 *
 * Upstream path being reproduced (DefaultAppSearchAlgorithm.getTitleMatchResult):
 *     StringMatcherUtility.matches(query.toLowerCase(), info.title.toString(), StringMatcher.getInstance())
 */
class DifferentialTest {

    private val labels = listOf(
        // Common Android and Xiaomi app labels.
        "Calculator", "Calendar", "Camera", "Chrome", "Clock", "Contacts", "Drive", "Files", "Gallery",
        "Gmail", "Google", "Google Maps", "Google Photos", "Google Play Store", "Play Store", "Keep Notes",
        "Messages", "Phone", "Photos", "Settings", "YouTube", "YouTube Music", "WhatsApp", "Instagram",
        "Facebook", "Messenger", "Spotify", "Netflix", "Amazon Shopping", "Uber", "Lyft", "Airbnb",
        "Booking.com", "Citymapper", "Mi Home", "Mi Fitness", "Mi Video", "Mi Browser", "Xiaomi Community",
        "GetApps", "Security", "Themes", "Notes", "Recorder", "Compass", "Weather", "Scanner", "Mi Remote",
        "ShareMe", "Cleaner", "Microsoft Teams", "Microsoft Outlook", "Microsoft 365 (Office)", "Slack",
        "Zoom", "Google Meet", "LinkedIn", "PayPal", "Revolut", "Wise", "Google Wallet", "Google Pay",
        "Duolingo", "TikTok", "Snapchat", "Telegram", "Signal", "Discord", "Reddit", "X", "Threads",
        "Pinterest", "Twitch", "Prime Video", "Disney+", "BBC iPlayer", "BBC Sounds", "Audible",
        "Kindle", "Goodreads", "Strava", "MyFitnessPal", "Headspace", "Calm", "1Password", "Bitwarden",
        "Authenticator", "Microsoft Authenticator", "Google Authenticator", "Termux", "F-Droid",
        "VLC", "MX Player", "HTMLViewer", "LEGO®Builder", "t-mobile", "Agar.io", "FC 25", "EA SPORTS FC™ 25",
        "Fit2", "CamScanner", "PDF Reader", "PDF+", "2048", "4K Video", "AR Zone", "IKEA", "H&M", "M&S",
        "C++ Compiler", "C# Notes", "100% Focus", "\$Wallet", "€uro Rates", "Plan-it", "Notes & Lists",
        "My O2", "O2 Priority", "Vodafone", "Three+", "3 UK",
        // Accents, ligatures and other scripts.
        "Café Finder", "Café Bar", "Müller Shop", "Crème Brûlée", "São Paulo Guide", "Ærø Ferry",
        "Straße Maps", "ﬁnance", "İstanbul Kart", "Işık", "Яндекс Карты", "ВКонтакте", "Ελληνικά",
        "Αθήνα Metro", "عربي", "עברית", "हिन्दी", "ไทย", "日本語入力", "カメラ", "ポケモンGO",
        "微信", "支付宝", "百度地图", "QQ音乐", "抖音", "美团", "Google 翻译", "카카오톡", "네이버",
        // Emoji, symbols and odd spacing.
        "🎉 Party", "Party 🎉", "😀Smile", "Stars ★★★", "A B Test", "Tab\tApp", "Line Break",
        "ALL CAPS APP", "camelCaseApp", "snake_case_app", "dot.separated.app", "UPPERlower", "ABCdef",
        "x86 Tools", "Ⅻ Roman", "½ Half", "a", "ab", "",
    )

    private val extraQueries = listOf(
        "cafe", "café", "CAFE", "muller", "mueller", "creme", "brulee", "sao", "aero", "strasse", "strase",
        "finance", "fi", "istanbul", "isik", "ısık", "i", "ı", "İ", "yandex", "яндекс", "карты", "αθηνα",
        "αθήνα", "metro", "信", "微", "支付", "地图", "音乐", "qq", "翻译", "카카오", "카", "カメ", "かめら",
        "go", "pokemon", "party", "🎉", "smile", "😀", "★", "stars", "b test", "a b", "app", "case",
        "separated", "lower", "def", "86", "x8", "roman", "half", "½", "xii", "maps google", "google maps",
        "play store", "store", "tube", "you", "out", "rome", "mail", "om", "o", " ", "  ", "c++", "c#", "+",
        "&", "h&m", "100", "%", "$", "€", "plan it", "it", "-", "lists", "o2", "2", "3", "uk", "zzzz",
        "á", "é", "￿", " ",
    )

    private val locales = listOf(
        Locale.US, Locale.GERMANY, Locale.forLanguageTag("tr-TR"), Locale.SIMPLIFIED_CHINESE,
        Locale.JAPAN, Locale.forLanguageTag("sv-SE"), Locale.forLanguageTag("ru-RU"), Locale.ROOT,
    )

    private fun queryPool(): List<String> {
        val pool = LinkedHashSet<String>()
        for (label in labels) {
            for (start in label.indices) {
                for (len in 1..minOf(6, label.length - start)) {
                    val q = label.substring(start, start + len)
                    pool += q
                    pool += q.lowercase(Locale.ROOT)
                    pool += Normalizer.normalize(q, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
                }
            }
        }
        pool += extraQueries
        return pool.toList()
    }

    @Test
    fun agreesWithLauncher3OnEveryPair() {
        val original = Locale.getDefault()
        val report = StringBuilder()
        val queries = queryPool()
        var totalPairs = 0L
        var totalMatches = 0L
        val mismatches = ArrayList<String>()
        try {
            for (locale in locales) {
                Locale.setDefault(locale)
                // Both sides build their Collator from the default locale, as Launcher3 does.
                val upstream = StringMatcherUtility.StringMatcher.getInstance()
                val strata = AppSearch()
                var pairs = 0L
                var matches = 0L
                for (label in labels) {
                    for (query in queries) {
                        val expected = StringMatcherUtility.matches(query.lowercase(Locale.getDefault()), label, upstream)
                        val actual = strata.matchesWordStart(query, label)
                        pairs++
                        if (expected) matches++
                        if (expected != actual && mismatches.size < 50) {
                            mismatches += "[$locale] query=\"$query\" label=\"$label\" launcher3=$expected strata=$actual"
                        }
                    }
                }
                totalPairs += pairs
                totalMatches += matches
                report.append("locale=${locale.toLanguageTag().ifEmpty { "root" }} pairs=$pairs launcher3Matches=$matches\n")
            }
        } finally {
            Locale.setDefault(original)
        }
        report.append("labels=${labels.size} queries=${queries.size} totalPairs=$totalPairs totalMatches=$totalMatches mismatches=${mismatches.size}\n")
        mismatches.forEach { report.append(it).append('\n') }
        File("build/differential-report.txt").writeText(report.toString())
        print(report)
        assertEquals("Disagreements with Launcher3:\n" + mismatches.joinToString("\n"), 0, mismatches.size)
    }

    /** Side-by-side samples for the written report. */
    @Test
    fun writeSamples() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val upstream = StringMatcherUtility.StringMatcher.getInstance()
            val strata = AppSearch()
            val samples = listOf(
                "you" to "YouTube", "tube" to "YouTube", "out" to "YouTube", "maps" to "Google Maps",
                "rome" to "Chrome", "mail" to "Gmail", "app" to "WhatsApp", "mobile" to "t-mobile",
                "io" to "Agar.io", "builder" to "LEGO®Builder", "viewer" to "HTMLViewer", "tml" to "HTMLViewer",
                "25" to "FC 25", "5" to "FC 25", "cafe" to "Café Finder", "muller" to "Müller Shop",
                "信" to "微信", "party" to "🎉 Party", "365" to "Microsoft 365 (Office)", "office" to "Microsoft 365 (Office)",
            )
            val out = StringBuilder("| Query | Label | Launcher3 | Strata word start | Strata MatchKind | Old Strata (substring) |\n|---|---|---|---|---|---|\n")
            for ((q, label) in samples) {
                val expected = StringMatcherUtility.matches(q.lowercase(Locale.getDefault()), label, upstream)
                out.append("| `$q` | $label | $expected | ${strata.matchesWordStart(q, label)} | ${strata.match(q, label) ?: "none"} | ${label.contains(q, ignoreCase = true)} |\n")
            }
            File("build/samples.md").writeText(out.toString())
            print(out)
        } finally {
            Locale.setDefault(original)
        }
    }
}
