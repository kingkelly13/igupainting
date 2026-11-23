package com.strataos.remote.data

/** What a saved remote controls. It picks the layout and which built-in codes are offered. */
enum class DeviceKind(val label: String, val noun: String) {
    TV("TV", "TV"),
    Streaming("Streaming box", "streaming box"),
    Lights("LED lights", "lights"),
    Audio("Soundbar or receiver", "speaker"),
    Projector("Projector", "projector"),
    Other("Something else", "device"),
    ;

    companion object {
        fun of(name: String?): DeviceKind = entries.firstOrNull { it.name == name } ?: Other
    }
}

/**
 * A button the layouts know how to place. [id] is what saved remotes store, [flipperName] is the
 * name used when exporting, and [aliases] are the names it may have in imported files
 * (compared after [normalize]).
 */
class StdKey(
    val id: String,
    val label: String,
    val flipperName: String,
    val repeatable: Boolean = false,
    val aliases: List<String> = emptyList(),
    /** For LED colour keys: the colour to draw, as 0xRRGGBB. */
    val swatch: Int? = null,
)

object Keys {
    private fun k(id: String, label: String, flipper: String, repeatable: Boolean = false, vararg aliases: String) =
        StdKey(id, label, flipper, repeatable, aliases.toList())

    val all: List<StdKey> = buildList {
        add(k("power", "Power", "Power", false, "power", "pwr", "onoff", "powertoggle", "standby", "togglepower"))
        add(k("power_on", "On", "Power_on", false, "poweron", "on", "discreteon"))
        add(k("power_off", "Off", "Power_off", false, "poweroff", "off", "discreteoff"))
        add(k("input", "Input", "Input", false, "input", "source", "src", "av", "tvav", "inputsource", "inputs", "sources"))
        add(k("vol_up", "Volume up", "Vol_up", true, "volup", "volumeup", "volplus", "volumeplus", "vup"))
        add(k("vol_down", "Volume down", "Vol_dn", true, "voldn", "voldown", "volumedown", "volminus", "volumeminus", "vdown", "voldwn"))
        add(k("mute", "Mute", "Mute", false, "mute", "volmute", "audiomute"))
        add(k("ch_up", "Channel up", "Ch_next", true, "chnext", "chup", "channelup", "chplus", "channelplus", "channext", "progup", "pageup"))
        add(k("ch_down", "Channel down", "Ch_prev", true, "chprev", "chdown", "channeldown", "chminus", "channelminus", "chdn", "progdown", "pagedown"))
        add(k("up", "Up", "Up", true, "up", "navup", "cursorup", "arrowup", "dpadup", "dirup"))
        add(k("down", "Down", "Down", true, "down", "navdown", "cursordown", "arrowdown", "dpaddown", "dirdown"))
        add(k("left", "Left", "Left", true, "left", "navleft", "cursorleft", "arrowleft", "dpadleft", "dirleft"))
        add(k("right", "Right", "Right", true, "right", "navright", "cursorright", "arrowright", "dpadright", "dirright"))
        add(k("ok", "OK", "Ok", false, "ok", "enter", "select", "confirm", "center", "centre", "dpadcenter"))
        add(k("back", "Back", "Back", false, "back", "return", "ret", "goback"))
        add(k("home", "Home", "Home", false, "home", "smarthub", "smart", "smarttv", "apps"))
        add(k("menu", "Menu", "Menu", false, "menu", "settings", "setup", "options", "option", "tools", "quickmenu"))
        add(k("exit", "Exit", "Exit", false, "exit", "quit", "close"))
        add(k("info", "Info", "Info", false, "info", "display", "osd", "status"))
        add(k("guide", "Guide", "Guide", false, "guide", "epg", "tvguide"))
        add(k("prev_ch", "Previous channel", "Prev_ch", false, "prevch", "last", "recall", "flashback", "jump", "prech", "qview", "lastch", "previouschannel"))
        for (n in 0..9) add(k("num_$n", "$n", "$n", false, "$n", "num$n", "digit$n", "key$n", "number$n", "btn$n"))
        add(k("play", "Play", "Play", false, "play"))
        add(k("pause", "Pause", "Pause", false, "pause"))
        add(k("play_pause", "Play/pause", "Play_pause", false, "playpause", "pauseplay"))
        add(k("stop", "Stop", "Stop", false, "stop"))
        add(k("rewind", "Rewind", "Rew", true, "rewind", "rew", "rwd", "rw", "fastrewind", "rev", "reverse", "back10"))
        add(k("fast_forward", "Fast forward", "Ff", true, "fastforward", "ff", "fwd", "forward", "fastfwd", "skip10"))
        add(k("record", "Record", "Rec", false, "record", "rec"))
        add(k("red", "Red", "Red", false, "red", "keyred", "a"))
        add(k("green", "Green", "Green", false, "green", "keygreen", "b"))
        add(k("yellow", "Yellow", "Yellow", false, "yellow", "keyyellow", "c"))
        add(k("blue", "Blue", "Blue", false, "blue", "keyblue", "d"))

        // LED light strips. The 24-key remote's colour grid, as R/G/B columns of five shades.
        add(k("bright_up", "Brighter", "Brightness_up", true, "brightup", "brightnessup", "brightplus", "brightnessplus", "dimup", "lighter"))
        add(k("bright_down", "Dimmer", "Brightness_dn", true, "brightdown", "brightnessdown", "brightminus", "brightnessminus", "dimdown", "darker", "brightdn"))
        add(k("flash", "Flash", "Flash", false, "flash"))
        add(k("strobe", "Strobe", "Strobe", false, "strobe"))
        add(k("fade", "Fade", "Fade", false, "fade"))
        add(k("smooth", "Smooth", "Smooth", false, "smooth"))
    } + ledColours

    /** LED colour keys, in the order of the 24-key remote's grid columns. */
    val ledColours: List<StdKey>
        get() = LedColourDefs

    private val byId: Map<String, StdKey> by lazy { all.associateBy { it.id } }

    fun get(id: String): StdKey? = byId[id]

    fun label(id: String): String = byId[id]?.label ?: id

    fun isRepeatable(id: String): Boolean = byId[id]?.repeatable ?: false

    /** Lower-case letters and digits only; "+" becomes "plus" and a trailing "-" becomes "minus". */
    fun normalize(name: String): String {
        var s = name.trim().lowercase()
        if (s.endsWith("-")) s = s.dropLast(1) + "minus"
        s = s.replace("+", "plus")
        return s.filter { it.isLetterOrDigit() }
    }

    /**
     * The standard key an imported button name refers to, if any. For LED lights, colour names
     * mean the light colour rather than a TV's coloured keys.
     */
    fun match(name: String, kind: DeviceKind): StdKey? {
        val n = normalize(name)
        if (n.isEmpty()) return null
        if (kind == DeviceKind.Lights) {
            LedColourDefs.firstOrNull { n in it.aliases }?.let { return it }
        }
        return all.firstOrNull { key ->
            n in key.aliases && !(kind == DeviceKind.Lights && key.id in setOf("red", "green", "yellow", "blue"))
        }
    }
}

private fun led(id: String, label: String, swatch: Int, vararg aliases: String) =
    StdKey(id, label, label.replace(' ', '_'), false, aliases.toList(), swatch)

private val LedColourDefs: List<StdKey> = listOf(
    led("led_r", "Red", 0xFF2A1F, "red", "r"),
    led("led_r1", "Orange red", 0xFF5A1F, "r1", "orangered", "darkorange"),
    led("led_r2", "Orange", 0xFF8A1F, "r2", "orange"),
    led("led_r3", "Amber", 0xFFB21F, "r3", "amber", "lightorange"),
    led("led_r4", "Yellow", 0xFFE01F, "r4", "yellow"),
    led("led_g", "Green", 0x22E05A, "green", "g"),
    led("led_g1", "Spring green", 0x22E59A, "g1", "lightgreen", "springgreen"),
    led("led_g2", "Cyan", 0x22D8D0, "g2", "cyan", "aqua"),
    led("led_g3", "Teal", 0x1FA9C8, "g3", "teal", "lightblue"),
    led("led_g4", "Sky blue", 0x2E86E8, "g4", "skyblue"),
    led("led_b", "Blue", 0x2F4BFF, "blue", "b"),
    led("led_b1", "Royal blue", 0x4E5BFF, "b1", "royalblue"),
    led("led_b2", "Violet", 0x7A4DFF, "b2", "violet", "purple"),
    led("led_b3", "Magenta", 0xB547F0, "b3", "magenta", "lightpurple"),
    led("led_b4", "Pink", 0xF04FB8, "b4", "pink"),
    led("led_w", "White", 0xF3F1EC, "white", "w"),
)
