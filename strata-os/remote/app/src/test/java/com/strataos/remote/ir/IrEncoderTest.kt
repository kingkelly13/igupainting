package com.strataos.remote.ir

import com.strataos.remote.data.CodeLibrary
import com.strataos.remote.data.Device
import com.strataos.remote.data.DeviceKind
import com.strataos.remote.data.Keys
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrEncoderTest {
    /** Reads a pulse-distance frame back as the MSB-first hex that code lists print. */
    private fun pulseDistanceWord(pattern: IntArray, threshold: Int): Long {
        var value = 0L
        var i = 2 // skip the leader mark and space
        while (i + 1 < pattern.size) {
            value = (value shl 1) or (if (pattern[i + 1] > threshold) 1L else 0L)
            i += 2
        }
        return value
    }

    private fun assertWellFormed(pattern: IntArray) {
        assertTrue("pattern must end on a mark", pattern.size % 2 == 1)
        assertTrue("timings must be positive", pattern.all { it > 0 })
    }

    @Test
    fun necMatchesTheLgPowerCode() {
        val t = IrEncoder.encode(IrSignal.Parsed(Protocol.NEC, 0x04, 0x08))
        assertEquals(38_000, t.frequency)
        assertWellFormed(t.first)
        assertEquals(9000, t.first[0])
        assertEquals(4500, t.first[1])
        assertEquals(1 + 1 + 64 + 1, t.first.size)
        assertEquals(0x20DF10EFL, pulseDistanceWord(t.first, 1000))
        assertArrayEquals(intArrayOf(9000, 2250, 560), t.repeat)
        assertEquals(108_000L, t.firstDurationUs + t.firstTailUs)
    }

    @Test
    fun necExtendedMatchesRokuAndLedCodes() {
        val roku = IrEncoder.encode(IrSignal.Parsed(Protocol.NECext, 0xC7EA, 0x17))
        assertEquals(0x57E3E817L, pulseDistanceWord(roku.first, 1000))
        // A 16-bit Flipper command (command + inverse) gives the same frame.
        val roku16 = IrEncoder.encode(IrSignal.Parsed(Protocol.NECext, 0xC7EA, 0xE817))
        assertArrayEquals(roku.first, roku16.first)
        val ledOn = IrEncoder.encode(IrSignal.Parsed(Protocol.NECext, 0xEF00, 0x03))
        assertEquals(0x00F7C03FL, pulseDistanceWord(ledOn.first, 1000))
    }

    @Test
    fun samsungMatchesTheSamsungPowerCode() {
        val t = IrEncoder.encode(IrSignal.Parsed(Protocol.Samsung32, 0x07, 0x02))
        assertWellFormed(t.first)
        assertEquals(4500, t.first[0])
        assertEquals(4500, t.first[1])
        assertEquals(0xE0E040BFL, pulseDistanceWord(t.first, 1000))
    }

    @Test
    fun toshibaAndEpsonCodes() {
        assertEquals(0x02FD48B7L, pulseDistanceWord(IrEncoder.encode(IrSignal.Parsed(Protocol.NEC, 0x40, 0x12)).first, 1000))
        assertEquals(0xC1AA09F6L, pulseDistanceWord(IrEncoder.encode(IrSignal.Parsed(Protocol.NECext, 0x5583, 0x90)).first, 1000))
    }

    @Test
    fun sircSendsThreeFramesOfTheSonyPowerCode() {
        val t = IrEncoder.encode(IrSignal.Parsed(Protocol.SIRC, 0x01, 0x15))
        assertEquals(40_000, t.frequency)
        assertWellFormed(t.first)
        // One frame: header + 12 bits = 13 marks and 12 spaces between them.
        assertEquals(25, t.repeat.size)
        assertEquals(2400, t.repeat[0])
        val marks = (2 until t.repeat.size step 2).map { t.repeat[it] }
        val bits = marks.map { it > 900 }
        // 0x15 = 0010101 sent LSB first, then address 1 = 00001 LSB first.
        assertEquals(listOf(true, false, true, false, true, false, false, true, false, false, false, false), bits)
        assertEquals(3 * 25 + 2, t.first.size)
        assertEquals(45_000L, t.repeatDurationUs + t.repeatTailUs)
    }

    @Test
    fun rc5IsManchesterCodedWithAToggle() {
        val off = IrEncoder.encode(IrSignal.Parsed(Protocol.RC5, 0x00, 0x0C), toggle = false)
        val on = IrEncoder.encode(IrSignal.Parsed(Protocol.RC5, 0x00, 0x0C), toggle = true)
        assertEquals(36_000, off.frequency)
        assertWellFormed(off.first)
        assertNotEquals(off.first.toList(), on.first.toList())
        // Every timing is one or two half-bits.
        assertTrue(off.first.all { it == 889 || it == 1778 })
        // 14 bits = 28 half-bits. The leading 1 starts with silence and the trailing 0
        // (0x0C ends in 0) ends with it; neither is sent.
        assertEquals(26 * 889, off.first.sum())
    }

    @Test
    fun rc6HasALeaderAndADoubleWidthToggle() {
        val t = IrEncoder.encode(IrSignal.Parsed(Protocol.RC6, 0x00, 0x0C))
        assertWellFormed(t.first)
        assertEquals(2664, t.first[0])
        assertEquals(888, t.first[1])
        assertTrue(t.first.all { it % 444 == 0 })
    }

    @Test
    fun kaseikyoBuildsThePanasonicPowerBytes() {
        val power = CodeLibrary.byId("panasonic_tv")!!.buttons.first { it.key == "power" }.signal as IrSignal.Parsed
        assertEquals(0x00200280L, power.address)
        assertEquals(0x3D0L, power.command)
        assertArrayEquals(intArrayOf(0x02, 0x20, 0x80, 0x00, 0x3D, 0xBD), IrEncoder.kaseikyoBytes(power.address, power.command))
        val exit = CodeLibrary.byId("panasonic_tv")!!.buttons.first { it.key == "exit" }.signal as IrSignal.Parsed
        assertEquals(0xD3, IrEncoder.kaseikyoBytes(exit.address, exit.command)[4])
        val t = IrEncoder.encode(power)
        assertWellFormed(t.first)
        assertEquals(1 + 1 + 96 + 1, t.first.size)
    }

    @Test
    fun everyLibraryCodeEncodesWithinTheTwoSecondLimit() {
        for (set in CodeLibrary.sets) {
            assertTrue(set.id, set.buttons.isNotEmpty())
            assertEquals("duplicate keys in ${set.id}", set.buttons.size, set.buttons.map { it.key }.toSet().size)
            for (button in set.buttons) {
                assertTrue("${set.id}/${button.key} is not a known key", button.key.startsWith("custom_") || Keys.get(button.key) != null)
                val t = IrEncoder.encode(button.signal)
                assertWellFormed(t.first)
                assertWellFormed(t.repeat)
                assertTrue(t.firstDurationUs < 2_000_000)
                assertTrue(t.firstTailUs > 0 && t.repeatTailUs > 0)
            }
        }
        assertTrue(CodeLibrary.forKind(DeviceKind.TV).size >= 8)
    }

    @Test
    fun prontoLearnedCodesBecomeRawTimings() {
        val signal = Pronto.parse("0000 006D 0002 0001 0157 00AC 0015 0016 0015 05E8") as IrSignal.Raw
        assertEquals(38_029, signal.frequency)
        assertEquals(4, signal.once.size)
        assertEquals(2, signal.repeat.size)
        assertEquals(9020.0, signal.once[0].toDouble(), 30.0)
        val t = IrEncoder.encode(signal)
        assertEquals(3, t.first.size) // the trailing space becomes the tail
        assertTrue(t.firstTailUs > 500)
    }

    @Test
    fun prontoRc5AndRc6Codes() {
        assertEquals(IrSignal.Parsed(Protocol.RC5, 0, 0x0C), Pronto.parse("5000 0073 0000 0001 0000 000C"))
        assertEquals(IrSignal.Parsed(Protocol.RC6, 0, 0x0C), Pronto.parse("6000 0073 0000 0002 0000 000C"))
    }

    @Test
    fun flipperFilesRoundTrip() {
        val text = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 08 00 00 00
            #
            name: Vol_up
            type: parsed
            protocol: NECext
            address: EA C7 00 00
            command: 0F F0 00 00
            #
            name: Netflix
            type: raw
            frequency: 38000
            duty_cycle: 0.330000
            data: 9024 4512 579 552 579
            #
            name: Weird
            type: parsed
            protocol: Nonsense
            address: 00 00 00 00
            command: 00 00 00 00
        """.trimIndent()
        val result = FlipperIr.parse(text)
        assertEquals(listOf("Power", "Vol_up", "Netflix"), result.entries.map { it.name })
        assertEquals(listOf("Weird"), result.skipped)
        assertEquals(IrSignal.Parsed(Protocol.NEC, 4, 8), result.entries[0].signal)
        assertEquals(IrSignal.Parsed(Protocol.NECext, 0xC7EA, 0xF00F), result.entries[1].signal)
        assertEquals(listOf(9024, 4512, 579, 552, 579), (result.entries[2].signal as IrSignal.Raw).once)

        val again = FlipperIr.parse(FlipperIr.write(result.entries))
        assertEquals(result.entries.map { it.signal }, again.entries.map { it.signal })
    }

    @Test
    fun importedNamesMapToStandardKeys() {
        assertEquals("vol_up", Keys.match("VOL+", DeviceKind.TV)?.id)
        assertEquals("vol_down", Keys.match("Vol_dn", DeviceKind.TV)?.id)
        assertEquals("vol_down", Keys.match("VOL-", DeviceKind.TV)?.id)
        assertEquals("ch_up", Keys.match("Ch_next", DeviceKind.TV)?.id)
        assertEquals("num_7", Keys.match("7", DeviceKind.TV)?.id)
        assertEquals("ok", Keys.match("Enter", DeviceKind.TV)?.id)
        assertEquals("red", Keys.match("Red", DeviceKind.TV)?.id)
        assertEquals("led_r", Keys.match("Red", DeviceKind.Lights)?.id)
        assertEquals("bright_up", Keys.match("Brightness+", DeviceKind.Lights)?.id)
        assertNull(Keys.match("Netflix", DeviceKind.TV))
    }

    @Test
    fun importKeepsUnknownAndDuplicateNamesAsCustomButtons() {
        val nec = IrSignal.Parsed(Protocol.NEC, 4, 8)
        val entries = listOf("Power", "POWER", "Netflix", "Vol_up").map { FlipperIr.Entry(it, nec) }
        val buttons = Device.buttonsFrom(entries, DeviceKind.TV)
        assertEquals(listOf("power", "custom_1", "custom_2", "vol_up"), buttons.map { it.key })
        assertEquals("Netflix", buttons[2].label)
        val device = Device("d", "TV", DeviceKind.TV, null, buttons)
        assertEquals("custom_5", device.newCustomKey())
        val exported = FlipperIr.parse(device.toFlipper()).entries.map { it.name }
        assertEquals(listOf("Power", "POWER", "Netflix", "Vol_up"), exported)
    }

    @Test
    fun patternBuilderMergesAndTrims() {
        val p = PatternBuilder()
        p.space(100) // dropped: patterns start with a mark
        p.mark(10); p.mark(5); p.space(3); p.space(4); p.mark(1); p.space(99)
        assertArrayEquals(intArrayOf(15, 7, 1), p.build())
    }
}
