package com.strataos.remote.data

import com.strataos.remote.ir.IrSignal
import com.strataos.remote.ir.Protocol

/** A saved or built-in button: a key id (standard, or `custom_*`), its label and its code. */
data class RemoteButton(val key: String, val label: String, val signal: IrSignal)

/** One brand's code set from the built-in library. */
class CodeSet(
    val id: String,
    val brand: String,
    val kinds: Set<DeviceKind>,
    /** Which models it is known to cover. */
    val note: String,
    val buttons: List<RemoteButton>,
) {
    /** The button the setup test sends: power if there is one, else "on". */
    val testButton: RemoteButton
        get() = buttons.firstOrNull { it.key == "power" }
            ?: buttons.firstOrNull { it.key == "power_on" }
            ?: buttons.first()

    /** A second button to confirm with, for when the device was already off. */
    val confirmButton: RemoteButton?
        get() = buttons.firstOrNull { it.key == "vol_up" }
            ?: buttons.firstOrNull { it.key == "led_r" }
            ?: buttons.firstOrNull { it.key == "power_off" }
}

private class SetBuilder(private val protocol: Protocol, private val address: Long) {
    val buttons = ArrayList<RemoteButton>()

    fun key(id: String, command: Int, protocol: Protocol = this.protocol, address: Long = this.address) {
        buttons.add(RemoteButton(id, Keys.label(id), IrSignal.Parsed(protocol, address, command.toLong())))
    }

    fun custom(id: String, label: String, command: Int) {
        buttons.add(RemoteButton(id, label, IrSignal.Parsed(protocol, address, command.toLong())))
    }

    fun digits(vararg commands: Int) = commands.forEachIndexed { n, c -> key("num_$n", c) }
}

private fun set(
    id: String,
    brand: String,
    kinds: Set<DeviceKind>,
    note: String,
    protocol: Protocol,
    address: Long,
    build: SetBuilder.() -> Unit,
) = CodeSet(id, brand, kinds, note, SetBuilder(protocol, address).apply(build).buttons)

/** Panasonic's Kaseikyo frame: vendor 0x2002, genre 8, in Flipper's address/command layout. */
private fun panasonicTv(build: PanasonicBuilder.() -> Unit): List<RemoteButton> = PanasonicBuilder().apply(build).buttons

private class PanasonicBuilder {
    val buttons = ArrayList<RemoteButton>()

    fun key(id: String, code: Int) {
        val address = (code.toLong() shr 6 shl 24) or 0x0020_0280L
        val command = (code.toLong() and 0x3F) shl 4
        buttons.add(RemoteButton(id, Keys.label(id), IrSignal.Parsed(Protocol.Kaseikyo, address, command)))
    }
}

/**
 * The built-in codes. They cover the brands' common remotes; anything else can be imported from
 * a Flipper `.ir` file or typed in per button.
 */
object CodeLibrary {
    private val tv = setOf(DeviceKind.TV)

    val sets: List<CodeSet> = listOf(
        set("samsung_tv", "Samsung", tv, "Samsung TVs from about 2008 on", Protocol.Samsung32, 0x07) {
            key("power", 0x02); key("power_on", 0x99); key("power_off", 0x98)
            key("input", 0x01); key("vol_up", 0x07); key("vol_down", 0x0B); key("mute", 0x0F)
            key("ch_up", 0x12); key("ch_down", 0x10); key("prev_ch", 0x13)
            key("up", 0x60); key("down", 0x61); key("left", 0x65); key("right", 0x62); key("ok", 0x68)
            key("back", 0x58); key("exit", 0x2D); key("home", 0x79); key("menu", 0x1A)
            key("info", 0x1F); key("guide", 0x4F)
            digits(0x11, 0x04, 0x05, 0x06, 0x08, 0x09, 0x0A, 0x0C, 0x0D, 0x0E)
            key("play", 0x47); key("pause", 0x4A); key("stop", 0x46)
            key("rewind", 0x45); key("fast_forward", 0x48); key("record", 0x49)
            key("red", 0x6C); key("green", 0x14); key("yellow", 0x15); key("blue", 0x16)
        },
        set("lg_tv", "LG", tv, "LG TVs, including webOS models", Protocol.NEC, 0x04) {
            key("power", 0x08); key("power_on", 0xC4); key("power_off", 0xC5)
            key("input", 0x0B); key("vol_up", 0x02); key("vol_down", 0x03); key("mute", 0x09)
            key("ch_up", 0x00); key("ch_down", 0x01); key("prev_ch", 0x1A)
            key("up", 0x40); key("down", 0x41); key("left", 0x07); key("right", 0x06); key("ok", 0x44)
            key("back", 0x28); key("exit", 0x5B); key("home", 0x7C); key("menu", 0x43)
            key("info", 0xAA); key("guide", 0xAB)
            digits(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19)
            key("play", 0xB0); key("pause", 0xBA); key("stop", 0xB1)
            key("rewind", 0x8F); key("fast_forward", 0x8E)
            key("red", 0x72); key("green", 0x71); key("yellow", 0x63); key("blue", 0x61)
        },
        set("sony_tv", "Sony", tv, "Sony Bravia TVs", Protocol.SIRC, 0x01) {
            key("power", 0x15); key("power_on", 0x2E); key("power_off", 0x2F)
            key("input", 0x25); key("vol_up", 0x12); key("vol_down", 0x13); key("mute", 0x14)
            key("ch_up", 0x10); key("ch_down", 0x11); key("prev_ch", 0x3B)
            key("up", 0x74); key("down", 0x75); key("left", 0x34); key("right", 0x33); key("ok", 0x65)
            key("home", 0x60); key("exit", 0x63); key("info", 0x3A); key("guide", 0x0E)
            digits(0x09, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
            val bravia = 0x97L
            key("back", 0x23, Protocol.SIRC15, bravia); key("menu", 0x36, Protocol.SIRC15, bravia)
            key("play", 0x1A, Protocol.SIRC15, bravia); key("pause", 0x19, Protocol.SIRC15, bravia)
            key("stop", 0x18, Protocol.SIRC15, bravia); key("rewind", 0x1B, Protocol.SIRC15, bravia)
            key("fast_forward", 0x1C, Protocol.SIRC15, bravia)
            key("red", 0x25, Protocol.SIRC15, bravia); key("green", 0x26, Protocol.SIRC15, bravia)
            key("yellow", 0x27, Protocol.SIRC15, bravia); key("blue", 0x24, Protocol.SIRC15, bravia)
        },
        set("roku", "Roku", setOf(DeviceKind.TV, DeviceKind.Streaming),
            "Roku TVs (TCL, Hisense, Sharp, Insignia and others) and Roku players with an IR remote",
            Protocol.NECext, 0xC7EA) {
            key("power", 0x17); key("vol_up", 0x0F); key("vol_down", 0x10); key("mute", 0x20)
            key("home", 0x03); key("back", 0x66)
            key("up", 0x19); key("down", 0x33); key("left", 0x1E); key("right", 0x2D); key("ok", 0x2A)
            key("menu", 0x61); key("play_pause", 0x4C); key("rewind", 0x34); key("fast_forward", 0x55)
            custom("custom_replay", "Instant replay", 0x78)
        },
        set("vizio_tv", "Vizio", tv, "Vizio TVs. Some navigation buttons vary by model.", Protocol.NEC, 0x04) {
            key("power", 0x08); key("input", 0x2F)
            key("vol_up", 0x02); key("vol_down", 0x03); key("mute", 0x09)
            key("ch_up", 0x00); key("ch_down", 0x01); key("prev_ch", 0x1A)
            key("up", 0x45); key("down", 0x46); key("left", 0x47); key("right", 0x48); key("ok", 0x44)
            key("menu", 0x4F); key("back", 0x4A)
            digits(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19)
        },
        set("philips_rc6", "Philips", tv, "Philips TVs from about 2005 on (RC6)", Protocol.RC6, 0x00) {
            key("power", 0x0C); key("input", 0x38)
            key("vol_up", 0x10); key("vol_down", 0x11); key("mute", 0x0D)
            key("ch_up", 0x4C); key("ch_down", 0x4D)
            key("up", 0x58); key("down", 0x59); key("left", 0x5A); key("right", 0x5B); key("ok", 0x5C)
            key("back", 0x0A); key("home", 0x54); key("menu", 0x40); key("info", 0x0F); key("guide", 0xCC)
            digits(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
            key("play", 0x2C); key("pause", 0x30); key("stop", 0x31)
            key("rewind", 0x2B); key("fast_forward", 0x28)
            key("red", 0x6D); key("green", 0x6E); key("yellow", 0x6F); key("blue", 0x70)
        },
        CodeSet(
            "panasonic_tv", "Panasonic", tv, "Panasonic Viera TVs",
            panasonicTv {
                key("power", 0x3D); key("input", 0x05)
                key("vol_up", 0x20); key("vol_down", 0x21); key("mute", 0x32)
                key("ch_up", 0x34); key("ch_down", 0x35); key("info", 0x39)
                key("up", 0x4A); key("down", 0x4B); key("left", 0x4E); key("right", 0x4F); key("ok", 0x49)
                key("back", 0x2B); key("exit", 0xD3)
                listOf(0x19, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18)
                    .forEachIndexed { n, c -> key("num_$n", c) }
            },
        ),
        set("toshiba_tv", "Toshiba", tv, "Toshiba TVs", Protocol.NEC, 0x40) {
            key("power", 0x12); key("input", 0x0F)
            key("vol_up", 0x1A); key("vol_down", 0x1E); key("mute", 0x10)
            key("ch_up", 0x1B); key("ch_down", 0x1F)
            digits(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
        },
        set("philips_rc5", "Philips (older)", tv, "Philips TVs before about 2005 (RC5)", Protocol.RC5, 0x00) {
            key("power", 0x0C); key("input", 0x38)
            key("vol_up", 0x10); key("vol_down", 0x11); key("mute", 0x0D)
            key("ch_up", 0x20); key("ch_down", 0x21); key("prev_ch", 0x22); key("info", 0x0F)
            digits(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
        },
        set("led_24", "24-key LED remote", setOf(DeviceKind.Lights),
            "LED strips and bulbs sold with the common 24-button remote (On, Off, 16 colours, 4 effects)",
            Protocol.NECext, 0xEF00) {
            key("bright_up", 0x00); key("bright_down", 0x01); key("power_off", 0x02); key("power_on", 0x03)
            key("led_r", 0x04); key("led_g", 0x05); key("led_b", 0x06); key("led_w", 0x07)
            key("led_r1", 0x08); key("led_g1", 0x09); key("led_b1", 0x0A); key("flash", 0x0B)
            key("led_r2", 0x0C); key("led_g2", 0x0D); key("led_b2", 0x0E); key("strobe", 0x0F)
            key("led_r3", 0x10); key("led_g3", 0x11); key("led_b3", 0x12); key("fade", 0x13)
            key("led_r4", 0x14); key("led_g4", 0x15); key("led_b4", 0x16); key("smooth", 0x17)
        },
        set("led_44", "44-key LED remote", setOf(DeviceKind.Lights),
            "LED strips sold with the 44-button remote. The main buttons only.",
            Protocol.NEC, 0x00) {
            key("power", 0x40); key("play_pause", 0x41)
            key("bright_up", 0x5C); key("bright_down", 0x5D)
            key("led_r", 0x58); key("led_g", 0x59); key("led_b", 0x45); key("led_w", 0x44)
        },
        set("epson_projector", "Epson", setOf(DeviceKind.Projector),
            "Epson projectors. Power only; add the rest yourself or import a file.",
            Protocol.NECext, 0x5583) {
            key("power", 0x90)
        },
    )

    fun forKind(kind: DeviceKind): List<CodeSet> = sets.filter { kind in it.kinds }

    fun byId(id: String): CodeSet? = sets.firstOrNull { it.id == id }
}
