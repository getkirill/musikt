package musikt

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Produces audio. Some support changing frequency and/or phase.
 */
interface Instrument {
    /**
     * Sample instrument.
     */
    fun sample(
        sampleRate: Int = 44100
    ): Sequence<Double> = sample({ generateSequence { Note.C4.frequency } }, { generateSequence { 0.0 } }, sampleRate)

    /**
     * Sample instrument with specified frequency.
     */
    fun sample(
        frequency: () -> Sequence<Double> = { generateSequence { Note.C4.frequency } },
        sampleRate: Int = 44100
    ): Sequence<Double> = sample(frequency, { generateSequence { 0.0 } }, sampleRate)

    /**
     * Sample instrument with specified frequency and phase.
     */
    fun sample(
        frequency: () -> Sequence<Double> = { generateSequence { Note.C4.frequency } },
        phase: () -> Sequence<Double> = { generateSequence { 0.0 } },
        sampleRate: Int = 44100
    ): Sequence<Double>

    fun sample(frequency: Double, sampleRate: Int = 44100) =
        sample({ generateSequence { frequency } }, sampleRate = sampleRate)
}

object SineWave : Instrument {
    override fun sample(
        frequency: () -> Sequence<Double>,
        phase: () -> Sequence<Double>,
        sampleRate: Int
    ): Sequence<Double> =
        sequence {
            val phaseStep = frequency().map { it / sampleRate }.iterator()
            var phase = 0.0

            while (true) {
                val sample = sin(2.0 * PI * phase)
                yield(sample)
                phase = (phase + phaseStep.next()) % 1.0
            }
        }
}

object TriangleWave : Instrument {
    override fun sample(
        frequency: () -> Sequence<Double>,
        phase: () -> Sequence<Double>,
        sampleRate: Int
    ): Sequence<Double> =
        sequence {
            val phaseStep = frequency().map { it / sampleRate }.iterator()
            var phase = 0.0

            while (true) {
                val sample = 2 * asin(sin(2.0 * PI * phase)) / PI
                yield(sample)
                phase = (phase + phaseStep.next()) % 1.0
            }
        }
}

object SawtoothWave : Instrument {
    override fun sample(
        frequency: () -> Sequence<Double>,
        phase: () -> Sequence<Double>,
        sampleRate: Int
    ): Sequence<Double> =
        sequence {
            val phaseStep = frequency().map { it / sampleRate }.iterator()
            var phase = 0.0

            while (true) {
                val sample = -phase.mod(2.0) - 1;
                yield(sample)
                phase = (phase + phaseStep.next()) % 1.0
            }
        }

}

object Silence : Instrument {
    override fun sample(sampleRate: Int): Sequence<Double> = sample()

    override fun sample(
        frequency: () -> Sequence<Double>,
        sampleRate: Int
    ): Sequence<Double> = sample()

    override fun sample(frequency: Double, sampleRate: Int): Sequence<Double> = sample()

    override fun sample(
        frequency: () -> Sequence<Double>,
        phase: () -> Sequence<Double>,
        sampleRate: Int
    ): Sequence<Double> =
        sample()

    fun sample() = generateSequence { 0.0 }
}

data class Oscilator(
    val instrument: Instrument,
    val pitch: Sequence<Double>.() -> Sequence<Double>,
    val phase: Sequence<Double>.() -> Sequence<Double>,
    val gain: () -> Sequence<Double>
)

open class OscilatorsInstrument(val oscilators: List<Oscilator>) : Instrument {
    override fun sample(
        frequency: () -> Sequence<Double>,
        phase: () -> Sequence<Double>,
        sampleRate: Int
    ): Sequence<Double> =
        mixAudio(
            oscilators.map {
                it.instrument.sample(
                    { it.pitch(frequency()) },
                    { it.phase(phase()) },
                    sampleRate
                )
            })
}

object PianoInstrument : FxInstrument(
    OscilatorsInstrument(
        listOf(
            Oscilator(SineWave, { this }, { this }, { generateSequence { 1.0 } }),
            Oscilator(SineWave, { map { it * 2 } }, { this }, { generateSequence { 0.5 } }),
            Oscilator(TriangleWave, { map { it * 3 } }, { this }, { generateSequence { 0.25 } }),
            Oscilator(SawtoothWave, { map { it * 4 } }, { this }, { generateSequence { 0.125 } }),
        )
    ), {
        lowPassFilter(generateSequence { 400.0 })
            .gain(
                envelope(
                    peak = 1.0,
                    sustain = 0.7,
                    attackDuration = 100.milliseconds,
                    decayDuration = 200.milliseconds,
                    sustainDuration = Duration.ZERO,
                    releaseDuration = 2.seconds
                )
            )
    })

fun Instrument.fx(block: Sequence<Double>.() -> Sequence<Double>) = FxInstrument(this, block)

open class FxInstrument(val backing: Instrument, val fx: Sequence<Double>.() -> Sequence<Double>) : Instrument {
    override fun sample(
        frequency: () -> Sequence<Double>,
        phase: () -> Sequence<Double>,
        sampleRate: Int
    ): Sequence<Double> =
        backing.sample(frequency, phase, sampleRate).fx()
}

data class Voice(val audioStream: Sequence<Double>, var samplesLeft: Long) {
    val audioStreamIterator by lazy { audioStream.iterator() }
}

/**
 * Allows sequencing different instruments.
 */
class VoiceInstrument : Instrument {
    private val activeVoices = mutableListOf<Voice>()

    fun play(
        instrument: Instrument, frequency: () -> Sequence<Double>,
        phase: () -> Sequence<Double> = { generateSequence { 0.0 } }, duration: Duration, sampleRate: Int = 44100
    ) {
        activeVoices.add(
            Voice(
                instrument.sample(frequency, phase, sampleRate),
                (duration / (1.0 / sampleRate).seconds).toLong()
            )
        )
    }

    override fun sample(
        sampleRate: Int
    ): Sequence<Double> = sequence {
        while (true) {
            activeVoices.removeIf { it.samplesLeft <= 0 }
            yield(if (activeVoices.isNotEmpty()) mixSamples(activeVoices.map { it.samplesLeft--; it.audioStreamIterator.next() }) else 0.0)
        }
    }

    override fun sample(
        frequency: () -> Sequence<Double>,
        phase: () -> Sequence<Double>,
        sampleRate: Int
    ): Sequence<Double> = sample(sampleRate)

    override fun sample(frequency: () -> Sequence<Double>, sampleRate: Int): Sequence<Double> = sample(sampleRate)
}