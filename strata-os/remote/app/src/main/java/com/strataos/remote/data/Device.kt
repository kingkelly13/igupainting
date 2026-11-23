package com.strataos.remote.data

import com.strataos.remote.ir.FlipperIr

/** A saved remote: what it controls and every button it has a code for. */
data class Device(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    /** The library code set it was made from, if any. */
    val source: String?,
    val buttons: List<RemoteButton>,
) {
    fun button(key: String): RemoteButton? = buttons.firstOrNull { it.key == key }

    fun has(key: String): Boolean = buttons.any { it.key == key }

    /** Adds or replaces the button for [button]'s key. */
    fun with(button: RemoteButton): Device {
        val index = buttons.indexOfFirst { it.key == button.key }
        val list = buttons.toMutableList()
        if (index >= 0) list[index] = button else list.add(button)
        return copy(buttons = list)
    }

    fun without(key: String): Device = copy(buttons = buttons.filterNot { it.key == key })

    /** A key id for a new custom button. */
    fun newCustomKey(): String {
        var n = buttons.size + 1
        while (has("custom_$n")) n++
        return "custom_$n"
    }

    fun toFlipper(): String = FlipperIr.write(
        buttons.map { b -> FlipperIr.Entry(Keys.get(b.key)?.flipperName ?: b.label, b.signal) },
    )

    companion object {
        /** Turns imported Flipper entries into buttons, mapping known names onto standard keys. */
        fun buttonsFrom(entries: List<FlipperIr.Entry>, kind: DeviceKind): List<RemoteButton> {
            val out = ArrayList<RemoteButton>()
            val used = HashSet<String>()
            var custom = 1
            for (entry in entries) {
                val std = Keys.match(entry.name, kind)
                if (std != null && used.add(std.id)) {
                    out.add(RemoteButton(std.id, std.label, entry.signal))
                } else {
                    while ("custom_$custom" in used) custom++
                    val key = "custom_$custom"
                    used.add(key)
                    out.add(RemoteButton(key, entry.name.replace('_', ' ').trim().ifEmpty { "Button" }, entry.signal))
                }
            }
            return out
        }
    }
}
