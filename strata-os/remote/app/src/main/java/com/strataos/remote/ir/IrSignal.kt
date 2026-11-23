package com.strataos.remote.ir

/**
 * The IR protocols Strata Remote can encode. Names match the Flipper Zero `.ir` file format, so
 * files from the community Flipper-IRDB import as-is, and address/command values mean the same
 * thing they do there.
 */
enum class Protocol(
    val flipperName: String,
    val label: String,
    val addressBits: Int,
    val commandBits: Int,
) {
    NEC("NEC", "NEC", 8, 8),
    NECext("NECext", "NEC extended", 16, 16),
    NEC42("NEC42", "NEC 42-bit", 13, 8),
    NEC42ext("NEC42ext", "NEC 42-bit extended", 26, 16),
    Samsung32("Samsung32", "Samsung", 8, 8),
    SIRC("SIRC", "Sony 12-bit", 5, 7),
    SIRC15("SIRC15", "Sony 15-bit", 8, 7),
    SIRC20("SIRC20", "Sony 20-bit", 13, 7),
    RC5("RC5", "Philips RC5", 5, 6),
    RC5X("RC5X", "Philips RC5X", 5, 7),
    RC6("RC6", "Philips RC6", 8, 8),
    Kaseikyo("Kaseikyo", "Panasonic (Kaseikyo)", 26, 10),
    RCA("RCA", "RCA", 4, 8),
    Pioneer("Pioneer", "Pioneer", 16, 16),
    ;

    /** Whether repeated frames carry a toggle bit that has to flip between separate presses. */
    val usesToggle: Boolean get() = this == RC5 || this == RC5X || this == RC6

    companion object {
        fun fromName(name: String): Protocol? {
            val wanted = name.trim()
            return entries.firstOrNull { it.flipperName.equals(wanted, ignoreCase = true) }
        }
    }
}

/** One button's worth of infrared: either a protocol message or raw timings. */
sealed interface IrSignal {
    /** A decoded message. [address] and [command] use the Flipper Zero conventions. */
    data class Parsed(val protocol: Protocol, val address: Long, val command: Long) : IrSignal

    /**
     * Raw timings in microseconds, alternating on (carrier) and off, starting with on.
     * [repeat] is what a held button keeps sending; when empty, [once] is repeated.
     */
    data class Raw(
        val frequency: Int,
        val once: List<Int>,
        val repeat: List<Int> = emptyList(),
        val dutyCycle: Float = 0.33f,
    ) : IrSignal
}

/** A short human description, e.g. "NEC · 0x04 · 0x08" or "Raw · 38 kHz". */
fun IrSignal.describe(): String = when (this) {
    is IrSignal.Parsed -> "${protocol.label} · ${hex(address)} · ${hex(command)}"
    is IrSignal.Raw -> "Raw · ${frequency / 1000} kHz · ${once.size + repeat.size} timings"
}

fun hex(value: Long): String {
    val digits = java.lang.Long.toHexString(value).uppercase()
    return "0x" + if (digits.length % 2 == 1) "0$digits" else digits
}
