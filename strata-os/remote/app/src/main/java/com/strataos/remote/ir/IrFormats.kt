package com.strataos.remote.ir

import kotlin.math.roundToInt

/** Pronto hex, the format most IR code websites and forums post codes in. */
object Pronto {
    /** Parses a Pronto code such as `0000 006D 0022 0002 0157 00AC ...`. */
    fun parse(text: String): IrSignal {
        val words = text.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map {
            require(it.length <= 4) { "Pronto codes are groups of four hex digits" }
            it.toIntOrNull(16) ?: throw IllegalArgumentException("\"$it\" isn't hex")
        }
        require(words.size >= 6) { "That's too short to be a Pronto code" }
        return when (words[0]) {
            0x0000 -> learned(words)
            0x5000 -> {
                val command = words[5].toLong()
                IrSignal.Parsed(if (command > 0x3F) Protocol.RC5X else Protocol.RC5, words[4].toLong(), command)
            }
            0x6000 -> IrSignal.Parsed(Protocol.RC6, words[4].toLong(), words[5].toLong())
            else -> throw IllegalArgumentException(
                "Pronto type %04X isn't supported. Codes starting with 0000 work.".format(words[0]),
            )
        }
    }

    private fun learned(words: List<Int>): IrSignal.Raw {
        val code = words[1]
        require(code > 0) { "The frequency word can't be 0000" }
        val cycleUs = code * 0.241246
        val frequency = (1_000_000 / cycleUs).roundToInt()
        val onceCount = words[2] * 2
        val repeatCount = words[3] * 2
        require(words.size >= 4 + onceCount + repeatCount) { "The code is shorter than its header says" }
        fun timings(from: Int, count: Int) = (from until from + count).map { maxOf(1, (words[it] * cycleUs).roundToInt()) }
        return IrSignal.Raw(
            frequency = frequency,
            once = timings(4, onceCount),
            repeat = timings(4 + onceCount, repeatCount),
        )
    }
}

/**
 * The Flipper Zero `.ir` text format. Thousands of remotes are published in it (the community
 * Flipper-IRDB), so it is what Strata Remote imports and exports.
 */
object FlipperIr {
    class Entry(val name: String, val signal: IrSignal)

    class Result(val entries: List<Entry>, val skipped: List<String>)

    fun parse(text: String): Result {
        val entries = ArrayList<Entry>()
        val skipped = ArrayList<String>()
        var fields = LinkedHashMap<String, String>()

        fun finish() {
            val name = fields["name"] ?: return
            val signal = runCatching { signalOf(fields) }.getOrNull()
            if (signal != null) entries.add(Entry(name, signal)) else skipped.add(name)
        }

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            when (key) {
                "filetype", "version" -> Unit
                "name" -> {
                    finish()
                    fields = LinkedHashMap()
                    fields["name"] = value
                }
                "data" -> fields["data"] = ((fields["data"] ?: "") + " " + value).trim()
                else -> fields[key] = value
            }
        }
        finish()
        require(entries.isNotEmpty() || skipped.isNotEmpty()) { "No buttons found. Is this a Flipper .ir file?" }
        return Result(entries, skipped)
    }

    private fun signalOf(fields: Map<String, String>): IrSignal? = when (fields["type"]?.lowercase()) {
        "parsed" -> {
            val protocol = Protocol.fromName(fields["protocol"] ?: "")
            if (protocol == null) {
                null
            } else {
                IrSignal.Parsed(protocol, bytesLe(fields["address"] ?: "0"), bytesLe(fields["command"] ?: "0"))
            }
        }
        "raw" -> {
            val data = (fields["data"] ?: "").split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt() }
            if (data.isEmpty()) {
                null
            } else {
                IrSignal.Raw(
                    frequency = fields["frequency"]?.toIntOrNull() ?: 38_000,
                    once = data,
                    dutyCycle = fields["duty_cycle"]?.toFloatOrNull() ?: 0.33f,
                )
            }
        }
        else -> null
    }

    /** "07 00 00 00" (little-endian bytes) → 7. A single number such as "0x07" also works. */
    fun bytesLe(text: String): Long {
        val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size == 1) return parts[0].removePrefix("0x").removePrefix("0X").toLong(16)
        var value = 0L
        parts.take(8).forEachIndexed { i, part -> value = value or (part.toLong(16) shl (8 * i)) }
        return value
    }

    fun formatLe(value: Long): String =
        (0 until 4).joinToString(" ") { "%02X".format((value shr (8 * it)) and 0xFF) }

    fun write(entries: List<Entry>): String = buildString {
        append("Filetype: IR signals file\n")
        append("Version: 1\n")
        for (entry in entries) {
            append("# \n")
            append("name: ").append(entry.name.replace('\n', ' ')).append('\n')
            when (val s = entry.signal) {
                is IrSignal.Parsed -> {
                    append("type: parsed\n")
                    append("protocol: ").append(s.protocol.flipperName).append('\n')
                    append("address: ").append(formatLe(s.address)).append('\n')
                    append("command: ").append(formatLe(s.command)).append('\n')
                }
                is IrSignal.Raw -> {
                    append("type: raw\n")
                    append("frequency: ").append(s.frequency).append('\n')
                    append("duty_cycle: ").append("%.6f".format(java.util.Locale.ROOT, s.dutyCycle)).append('\n')
                    append("data: ").append(s.once.ifEmpty { s.repeat }.joinToString(" ")).append('\n')
                }
            }
        }
    }
}
