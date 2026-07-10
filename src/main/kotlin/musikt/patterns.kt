package musikt

import kotlin.time.Duration

sealed interface PatternEvent {
    data class PlayNote(val frequency: Sequence<Double>, val velocity: Double, val duration: Duration) : PatternEvent
    data class Rest(val duration: Duration) : PatternEvent
}

class Pattern(val sequence: Sequence<PatternEvent>) : Sequence<PatternEvent> by sequence {
    fun repeating() = Pattern(sequence {
        while (true) {
            yieldAll(this@Pattern)
        }
    })

    fun repeat(n: Int) = Pattern(sequence {
        repeat(n) {
            yieldAll(this@Pattern)
        }
    })
}

class PatternBuilder {
    val events = mutableListOf<PatternEvent>()
    fun note(note: Note, duration: Duration, velocity: Double = 1.0) =
        note(generateSequence { note.frequency }, duration, velocity)

    fun note(frequency: Sequence<Double>, duration: Duration, velocity: Double = 1.0) {
        events.add(PatternEvent.PlayNote(frequency, velocity, duration))
    }

    fun play(note: Note, duration: Duration, velocity: Double = 1.0) {
        note(note, duration, velocity)
        rest(duration)
    }

    fun rest(duration: Duration) {
        events.add(PatternEvent.Rest(duration))
    }

    fun build(): Pattern = Pattern(events.asSequence())
}

fun pattern(block: PatternBuilder.() -> Unit): Pattern {
    val patternBuilder = PatternBuilder()
    patternBuilder.block()
    return patternBuilder.build()
}