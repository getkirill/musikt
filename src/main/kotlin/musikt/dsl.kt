package musikt

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class VoiceDslEvent {
    data class Play(val instrument: Instrument, val frequency: () -> Sequence<Double>, val duration: Duration) :
        VoiceDslEvent() {
        override fun toString(): String {
            return "Play(instrument=$instrument, frequency=$frequency, duration=$duration)"
        }
    }

    data class Wait(val duration: Duration) : VoiceDslEvent() {
        override fun toString(): String {
            return "Wait(duration=$duration)"
        }
    }
}

class VoiceDsl {
    var waitAfterEnd = 5.seconds
    var sampleRate: Int = 44100
    private val voiceInstrument = VoiceInstrument()
    private val events = mutableListOf<VoiceDslEvent>()

    fun play(
        instrument: Instrument, frequency: () -> Sequence<Double>, duration: Duration, cutoffBuffer: Duration = duration * 0.25
    ) {
        pushNote(instrument, frequency, duration + cutoffBuffer)
        wait(duration)
    }

    fun play(instrument: Instrument, note: Note, duration: Duration, cutoffBuffer: Duration = duration * 0.25) =
        play(instrument, { generateSequence { note.frequency } }, duration, cutoffBuffer)

    fun pushNote(
        instrument: Instrument, frequency: () -> Sequence<Double>, duration: Duration
    ) {
        events.add(VoiceDslEvent.Play(instrument, frequency, duration))
    }

    fun pushNote(instrument: Instrument, note: Note, duration: Duration) =
        pushNote(instrument, { generateSequence { note.frequency } }, duration)

    fun wait(duration: Duration) {
        events.add(VoiceDslEvent.Wait(duration))
    }

    fun build(): Sequence<Double> = sequence {
        val voiceInstrumentStream = voiceInstrument.sample(sampleRate)
        for (event in events) {
            when (event) {
                is VoiceDslEvent.Play -> {
                    voiceInstrument.play(event.instrument, event.frequency, duration = event.duration)
                }

                is VoiceDslEvent.Wait -> {
                    yieldAll(voiceInstrumentStream.take((event.duration / (1.0 / sampleRate).seconds).toInt()))
                }
            }
        }
        yieldAll(voiceInstrumentStream.sampleAudio(waitAfterEnd, sampleRate))
    }
}

fun voice(block: VoiceDsl.() -> Unit): Sequence<Double> {
    val dsl = VoiceDsl()
    dsl.block()
    return dsl.build()
}