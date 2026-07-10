package musikt

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

/**
 * A single note or sound.
 */
interface Voice : Iterator<Double> {
    /**
     * Voice frequency.
     */
    val frequency: Iterator<Double>

    /**
     * Notifies voice to start synthesizing release fx
     */
    fun release() {}
}

interface Instrument {
    /**
     * Creates a new voice to play.
     */
    fun voice(frequency: Sequence<Double>, velocity: Double, sampleRate: Int = 44100): Voice
}

abstract class OscilatorVoice(override val frequency: Iterator<Double>, val sampleRate: Int = 44100) : Voice {
    val phaseStep get() = frequency.next() / sampleRate
    var phase = 0.0
    var active = true
    abstract fun sample(phase: Double): Double
    override fun next(): Double {
        phase = (phase + phaseStep) % 1.0
        return sample(phase)
    }

    override fun release() {
        active = false
    }

    override fun hasNext() = active
}

object SilentVoice : Voice {
    override val frequency: Iterator<Double> = generateSequence { 0.0 }.iterator()
    override fun next(): Double = 0.0
    override fun hasNext(): Boolean = false
}


class FxVoice(val backing: Voice, val fx: Sequence<Double>.() -> Sequence<Double>) : Voice {
    override val frequency: Iterator<Double> = backing.frequency

    private val transformed by lazy { backing.asSequence().fx().iterator() }

    override fun hasNext(): Boolean = transformed.hasNext()

    override fun next(): Double = transformed.next()
    override fun release() = backing.release()
}

class FxInstrument(val backing: Instrument, val fx: Sequence<Double>.() -> Sequence<Double>) : Instrument {
    override fun voice(frequency: Sequence<Double>, velocity: Double, sampleRate: Int): Voice =
        FxVoice(backing.voice(frequency, velocity, sampleRate), fx)
}

fun Instrument.fx(fx: Sequence<Double>.() -> Sequence<Double>) = FxInstrument(this, fx)

object SineWave : Instrument {
    override fun voice(frequency: Sequence<Double>, velocity: Double, sampleRate: Int): Voice =
        object : OscilatorVoice(frequency.iterator(), sampleRate) {
            override fun sample(phase: Double): Double = sin(2.0 * PI * phase)
        }
}

object TriangleWave : Instrument {
    override fun voice(frequency: Sequence<Double>, velocity: Double, sampleRate: Int): Voice =
        object : OscilatorVoice(frequency.iterator(), sampleRate) {
            override fun sample(phase: Double): Double = 2 * asin(sin(2.0 * PI * phase)) / PI
        }
}

object SawtoothWave : Instrument {
    override fun voice(frequency: Sequence<Double>, velocity: Double, sampleRate: Int): Voice =
        object : OscilatorVoice(frequency.iterator(), sampleRate) {
            override fun sample(phase: Double): Double = -phase.mod(2.0) - 1
        }
}

object Silence : Instrument {
    override fun voice(frequency: Sequence<Double>, velocity: Double, sampleRate: Int): Voice = SilentVoice
}

class Synthesizer(val instrument: Instrument, val sampleRate: Int) {
    val activeVoices = mutableMapOf<Voice, Long>()
    val sampleDuration = (1.0 / sampleRate).seconds

    fun synthesize(pattern: Pattern): Sequence<Double> = sequence {
        var currentSampleIndex = 0L

        for (event in pattern) {
            when (event) {
                is PatternEvent.PlayNote -> {
                    val durationSamples =
                        (event.duration.inWholeNanoseconds.toDouble() / sampleDuration.inWholeNanoseconds.toDouble()).toLong()
                    val endSampleIndex = currentSampleIndex + durationSamples
                    val voice = instrument.voice(event.frequency, event.velocity, sampleRate)

                    activeVoices[voice] = endSampleIndex
                }

                is PatternEvent.Rest -> {
                    val restSamples =
                        (event.duration.inWholeNanoseconds.toDouble() / sampleDuration.inWholeNanoseconds.toDouble()).toLong()
                    val targetSampleIndex = currentSampleIndex + restSamples

                    while (currentSampleIndex < targetSampleIndex) {
                        cleanupExpiredVoices(currentSampleIndex)
                        yield(mixActiveVoices())
                        currentSampleIndex++
                    }
                }
            }
        }

        while (activeVoices.isNotEmpty()) {
            cleanupExpiredVoices(currentSampleIndex)
            yield(mixActiveVoices())
            currentSampleIndex++
        }
    }

    private fun mixActiveVoices(): Double {
        var mixedSample = 0.0
        val iterator = activeVoices.keys.iterator()

        while (iterator.hasNext()) {
            val voice = iterator.next()
            if (voice.hasNext()) {
                mixedSample += voice.next()
            } else {
                iterator.remove()
            }
        }
        return mixedSample
    }

    private fun cleanupExpiredVoices(currentSampleIndex: Long) {
        val iterator = activeVoices.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentSampleIndex >= entry.value) {
                entry.key.release()
                iterator.remove()
            }
        }
    }
}