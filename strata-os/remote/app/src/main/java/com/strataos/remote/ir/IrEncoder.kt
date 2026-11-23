package com.strataos.remote.ir

/**
 * What the blaster sends for one button.
 *
 * [first] goes out on every press. While a repeatable button is held, [repeat] is sent again
 * and again. Each pattern alternates on/off microseconds, starts with on and ends with on; the
 * matching `...TailUs` is the silence to leave after it before anything else is sent.
 */
class IrTransmission(
    val frequency: Int,
    val first: IntArray,
    val firstTailUs: Int,
    val repeat: IntArray,
    val repeatTailUs: Int,
) {
    val firstDurationUs: Long get() = first.sumOf { it.toLong() }
    val repeatDurationUs: Long get() = repeat.sumOf { it.toLong() }
}

/** Builds alternating mark/space timings, merging neighbours of the same kind. */
internal class PatternBuilder {
    private val out = ArrayList<Int>()

    val durationUs: Int get() = out.sum()

    fun mark(us: Int) {
        if (us <= 0) return
        if (out.size % 2 == 1) out[out.lastIndex] += us else out.add(us)
    }

    fun space(us: Int) {
        if (us <= 0 || out.isEmpty()) return // a pattern can't start with silence
        if (out.size % 2 == 0) out[out.lastIndex] += us else out.add(us)
    }

    /** Appends [pattern], which starts with a mark. */
    fun append(pattern: IntArray) {
        pattern.forEachIndexed { i, us -> if (i % 2 == 0) mark(us) else space(us) }
    }

    /** The pattern without trailing silence, so it always ends on a mark. */
    fun build(): IntArray {
        val end = if (out.size % 2 == 0) out.size - 1 else out.size
        return IntArray(maxOf(end, 0)) { out[it] }
    }
}

/** Bits in the order they go on air. */
internal class Bits {
    val list = ArrayList<Boolean>()

    fun lsb(value: Long, count: Int): Bits {
        for (i in 0 until count) list.add((value shr i) and 1L == 1L)
        return this
    }

    fun msb(value: Long, count: Int): Bits {
        for (i in count - 1 downTo 0) list.add((value shr i) and 1L == 1L)
        return this
    }
}

object IrEncoder {
    /** Encodes [signal]. [toggle] is the RC5/RC6 toggle bit; flip it between separate presses. */
    fun encode(signal: IrSignal, toggle: Boolean = false): IrTransmission = when (signal) {
        is IrSignal.Raw -> raw(signal)
        is IrSignal.Parsed -> parsed(signal, toggle)
    }

    private fun parsed(s: IrSignal.Parsed, toggle: Boolean): IrTransmission {
        val a = s.address
        val c = s.command
        return when (s.protocol) {
            Protocol.NEC -> nec(necBits(a, c, extended = a > 0xFF))
            Protocol.NECext -> nec(necBits(a, c, extended = true))
            Protocol.NEC42 -> nec(
                Bits().lsb(a and 0x1FFF, 13).lsb(a.inv() and 0x1FFF, 13).lsb(c and 0xFF, 8).lsb(c.inv() and 0xFF, 8),
            )
            Protocol.NEC42ext -> nec(Bits().lsb(a and 0x3FFFFFF, 26).lsb(c and 0xFFFF, 16))
            Protocol.Samsung32 -> samsung(a and 0xFF, c and 0xFF)
            Protocol.SIRC -> sirc(c, a, 5)
            Protocol.SIRC15 -> sirc(c, a, 8)
            Protocol.SIRC20 -> sirc(c, a, 13)
            Protocol.RC5 -> rc5(a, c, extended = c > 0x3F, toggle = toggle)
            Protocol.RC5X -> rc5(a, c, extended = true, toggle = toggle)
            Protocol.RC6 -> rc6(a, c, toggle)
            Protocol.Kaseikyo -> kaseikyo(a, c)
            Protocol.RCA -> rca(a, c)
            Protocol.Pioneer -> pioneer(a, c)
        }
    }

    // ---- NEC family: 38 kHz, 9 ms + 4.5 ms leader, pulse-distance bits, LSB first. ----

    private const val NEC_PERIOD = 108_000

    private fun necBits(address: Long, command: Long, extended: Boolean): Bits {
        val bits = Bits()
        if (extended) bits.lsb(address and 0xFFFF, 16) else bits.lsb(address and 0xFF, 8).lsb(address.inv() and 0xFF, 8)
        // Flipper stores NECext commands as 16 bits (command + its inverse). An 8-bit value gets
        // its inverse added, as in plain NEC.
        if (command > 0xFF) bits.lsb(command and 0xFFFF, 16) else bits.lsb(command, 8).lsb(command.inv() and 0xFF, 8)
        return bits
    }

    private fun nec(bits: Bits): IrTransmission {
        val frame = pulseDistance(9000, 4500, bits, 560, 1690, 560, 560)
        val repeat = PatternBuilder().apply { mark(9000); space(2250); mark(560) }.build()
        return IrTransmission(
            frequency = 38_000,
            first = frame,
            firstTailUs = NEC_PERIOD - frame.sum(),
            repeat = repeat,
            repeatTailUs = NEC_PERIOD - repeat.sum(),
        )
    }

    // ---- Samsung32: NEC timing with a 4.5 ms + 4.5 ms leader and the address sent twice. ----

    private fun samsung(address: Long, command: Long): IrTransmission {
        val bits = Bits().lsb(address, 8).lsb(address, 8).lsb(command, 8).lsb(command.inv() and 0xFF, 8)
        val frame = pulseDistance(4500, 4500, bits, 560, 1690, 560, 560)
        return IrTransmission(38_000, frame, NEC_PERIOD - frame.sum(), frame, NEC_PERIOD - frame.sum())
    }

    // ---- Sony SIRC: 40 kHz, pulse-width bits, 7-bit command then address, LSB first. ----

    private const val SIRC_PERIOD = 45_000

    private fun sirc(command: Long, address: Long, addressBits: Int): IrTransmission {
        val bits = Bits().lsb(command and 0x7F, 7).lsb(address and ((1L shl addressBits) - 1), addressBits)
        val p = PatternBuilder()
        p.mark(2400)
        p.space(600)
        for (bit in bits.list) {
            p.mark(if (bit) 1200 else 600)
            p.space(600)
        }
        val frame = p.build()
        val gap = SIRC_PERIOD - frame.sum()
        // Sony devices only act on a code they have seen at least three times.
        val three = PatternBuilder().apply {
            append(frame); space(gap); append(frame); space(gap); append(frame)
        }.build()
        return IrTransmission(40_000, three, gap, frame, gap)
    }

    // ---- Philips RC5: 36 kHz, Manchester coded, 889 us half-bits, MSB first. ----

    private const val RC5_HALF = 889
    private const val RC5_PERIOD = 113_778

    private fun rc5(address: Long, command: Long, extended: Boolean, toggle: Boolean): IrTransmission {
        // RC5X reuses the second start bit as an inverted 7th command bit.
        val s2 = if (extended) (command and 0x40) == 0L else true
        val bits = Bits()
        bits.list.add(true)
        bits.list.add(s2)
        bits.list.add(toggle)
        bits.msb(address and 0x1F, 5).msb(command and 0x3F, 6)
        val p = PatternBuilder()
        for (bit in bits.list) {
            if (bit) { p.space(RC5_HALF); p.mark(RC5_HALF) } else { p.mark(RC5_HALF); p.space(RC5_HALF) }
        }
        val frame = p.build()
        return IrTransmission(36_000, frame, RC5_PERIOD - frame.sum(), frame, RC5_PERIOD - frame.sum())
    }

    // ---- Philips RC6 mode 0: 36 kHz, 444 us units, Manchester with a double-width toggle bit. ----

    private const val RC6_T = 444
    private const val RC6_PERIOD = 107_000

    private fun rc6(address: Long, command: Long, toggle: Boolean): IrTransmission {
        val p = PatternBuilder()
        p.mark(6 * RC6_T)
        p.space(2 * RC6_T)
        fun bit(one: Boolean, width: Int = RC6_T) {
            if (one) { p.mark(width); p.space(width) } else { p.space(width); p.mark(width) }
        }
        bit(true) // start bit
        repeat(3) { bit(false) } // mode 0
        bit(toggle, 2 * RC6_T)
        Bits().msb(address and 0xFF, 8).msb(command and 0xFF, 8).list.forEach { bit(it) }
        val frame = p.build()
        return IrTransmission(36_000, frame, RC6_PERIOD - frame.sum(), frame, RC6_PERIOD - frame.sum())
    }

    // ---- Kaseikyo (Panasonic, Denon, JVC and others): 37 kHz, 48 bits, LSB first. ----

    private const val KASEIKYO_T = 432

    /** The six bytes on air, using Flipper's address/command layout. */
    fun kaseikyoBytes(address: Long, command: Long): IntArray {
        val id = ((address shr 24) and 0x3).toInt()
        val vendor = ((address shr 8) and 0xFFFF).toInt()
        val genre1 = ((address shr 4) and 0xF).toInt()
        val genre2 = (address and 0xF).toInt()
        val data = (command and 0x3FF).toInt()
        val b = IntArray(6)
        b[0] = vendor and 0xFF
        b[1] = vendor shr 8
        val vp = b[0] xor b[1]
        b[2] = ((vp and 0xF) xor (vp shr 4)) or (genre1 shl 4)
        b[3] = genre2 or ((data and 0xF) shl 4)
        b[4] = (id shl 6) or (data shr 4)
        b[5] = b[2] xor b[3] xor b[4]
        return b
    }

    private fun kaseikyo(address: Long, command: Long): IrTransmission {
        val bits = Bits()
        kaseikyoBytes(address, command).forEach { bits.lsb(it.toLong(), 8) }
        val t = KASEIKYO_T
        val frame = pulseDistance(8 * t, 4 * t, bits, t, 3 * t, t, t)
        return IrTransmission(37_000, frame, 74_000, frame, 74_000)
    }

    // ---- RCA: 56 kHz, 4-bit address + 8-bit command, then both inverted, MSB first. ----

    private fun rca(address: Long, command: Long): IrTransmission {
        val bits = Bits().msb(address and 0xF, 4).msb(command and 0xFF, 8)
            .msb(address.inv() and 0xF, 4).msb(command.inv() and 0xFF, 8)
        val frame = pulseDistance(4000, 4000, bits, 500, 2000, 1000, 500)
        return IrTransmission(56_000, frame, 8_000, frame, 8_000)
    }

    // ---- Pioneer: NEC framing at 40 kHz. A 16-bit address/command is sent as two frames. ----

    private fun pioneer(address: Long, command: Long): IrTransmission {
        fun frame(a: Long, c: Long): IntArray {
            val bits = Bits().lsb(a and 0xFF, 8).lsb(a.inv() and 0xFF, 8).lsb(c and 0xFF, 8).lsb(c.inv() and 0xFF, 8)
            return pulseDistance(8500, 4250, bits, 500, 1500, 500, 500)
        }
        val one = frame(address, command)
        val full = if (address > 0xFF || command > 0xFF) {
            PatternBuilder().apply { append(one); space(25_000); append(frame(address shr 8, command shr 8)) }.build()
        } else {
            one
        }
        return IrTransmission(40_000, full, 25_000, full, 25_000)
    }

    // ---- Raw timings, e.g. from a Flipper raw signal or a Pronto code. ----

    private const val RAW_DEFAULT_TAIL = 40_000

    private fun raw(s: IrSignal.Raw): IrTransmission {
        val once = s.once.ifEmpty { s.repeat }
        val again = s.repeat.ifEmpty { once }
        require(once.isNotEmpty()) { "This raw signal has no timings" }
        val (first, firstTail) = trimTail(once)
        val (repeat, repeatTail) = trimTail(again)
        return IrTransmission(s.frequency, first, firstTail, repeat, repeatTail)
    }

    /** Splits a trailing silence off [timings], which may end on a space (as Pronto codes do). */
    private fun trimTail(timings: List<Int>): Pair<IntArray, Int> {
        val p = PatternBuilder()
        timings.forEachIndexed { i, us -> if (i % 2 == 0) p.mark(us) else p.space(us) }
        val total = p.durationUs
        val pattern = p.build()
        val tail = total - pattern.sum()
        return pattern to if (tail > 0) tail else RAW_DEFAULT_TAIL
    }

    private fun pulseDistance(
        leaderMark: Int,
        leaderSpace: Int,
        bits: Bits,
        mark: Int,
        oneSpace: Int,
        zeroSpace: Int,
        stopMark: Int,
    ): IntArray {
        val p = PatternBuilder()
        p.mark(leaderMark)
        p.space(leaderSpace)
        for (bit in bits.list) {
            p.mark(mark)
            p.space(if (bit) oneSpace else zeroSpace)
        }
        p.mark(stopMark)
        return p.build()
    }
}
