package com.strataos.remote.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Sends codes through the phone's IR blaster (Android's ConsumerIrManager).
 *
 * Everything goes out on one background thread, in order, with each protocol's gap kept between
 * frames. A held button behaves like a held button on a real remote: its repeat frame keeps going
 * out until [release].
 */
class IrBlaster(context: Context) {
    private val manager: ConsumerIrManager? = context.getSystemService(ConsumerIrManager::class.java)

    val hasEmitter: Boolean = runCatching { manager?.hasIrEmitter() == true }.getOrDefault(false)

    /** Carrier ranges the hardware reports, in Hz. Many phones report none, which means "any". */
    val frequencyRanges: List<IntRange> = runCatching {
        manager?.carrierFrequencies?.map { it.minFrequency..it.maxFrequency }
    }.getOrNull().orEmpty()

    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ir-blaster").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val main = Handler(Looper.getMainLooper())

    private var holdJob: Job? = null
    private val toggles = HashMap<Protocol, Boolean>()

    /** Only touched on the blaster thread: when the IR line is free for the next frame. */
    private var freeAtNanos = 0L

    /**
     * Sends [signal]. For a [repeatable] button the repeat frame keeps going out until [release].
     * [onSent] and [onError] are called on the main thread.
     */
    fun press(
        signal: IrSignal,
        repeatable: Boolean,
        onSent: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        if (!hasEmitter) {
            onError("This phone has no IR blaster")
            return
        }
        val toggle = if (signal is IrSignal.Parsed && signal.protocol.usesToggle) {
            val next = !(toggles[signal.protocol] ?: true)
            toggles[signal.protocol] = next
            next
        } else {
            false
        }
        val tx = try {
            IrEncoder.encode(signal, toggle)
        } catch (e: IllegalArgumentException) {
            onError(e.message ?: "That code can't be sent")
            return
        }
        if (tx.firstDurationUs >= MAX_PATTERN_US) {
            onError("That code is longer than the 2-second limit")
            return
        }
        holdJob?.cancel()
        // ATOMIC and NonCancellable: a quick tap that is released before its turn comes still
        // sends its code once. Only the hold that follows can be cut short.
        holdJob = scope.launch(start = CoroutineStart.ATOMIC) {
            val sent = withContext(NonCancellable) { send(tx.frequency, tx.first, tx.firstTailUs, onError) }
            if (!sent) return@launch
            main.post(onSent)
            if (!repeatable) return@launch
            while (isActive) {
                waitUntilFree()
                if (!isActive) break
                if (!send(tx.frequency, tx.repeat, tx.repeatTailUs, onError)) break
            }
        }
    }

    /** Stops a held button. */
    fun release() {
        holdJob?.cancel()
        holdJob = null
    }

    private suspend fun waitUntilFree() {
        val waitNanos = freeAtNanos - SystemClock.elapsedRealtimeNanos()
        if (waitNanos > 0) delay((waitNanos + 999_999) / 1_000_000)
    }

    private suspend fun send(frequency: Int, pattern: IntArray, tailUs: Int, onError: (String) -> Unit): Boolean {
        waitUntilFree()
        val start = SystemClock.elapsedRealtimeNanos()
        val ok = try {
            manager?.transmit(fitFrequency(frequency), pattern)
            true
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            main.post { onError("The IR blaster refused the code ($message)") }
            false
        }
        freeAtNanos = start + (pattern.sumOf { it.toLong() } + tailUs) * 1000
        return ok
    }

    /** The protocol's carrier, moved into the nearest range the hardware supports. */
    fun fitFrequency(frequency: Int): Int {
        if (frequencyRanges.isEmpty() || frequencyRanges.any { frequency in it }) return frequency
        return frequencyRanges
            .map { frequency.coerceIn(it.first, it.last) }
            .minBy { kotlin.math.abs(it - frequency) }
    }

    private companion object {
        /** ConsumerIrManager rejects patterns longer than two seconds. */
        const val MAX_PATTERN_US = 2_000_000L
    }
}
