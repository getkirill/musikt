package musikt

import kotlin.time.Duration.Companion.seconds

fun main() {
    val timing = Timing(120.0)
    val key = Note.C4.semitones(8).major()
    val lead = PianoInstrument
    voice {
        listOf(
            key.note(0) to timing.whole,
            null to timing.eight,
            key.note(0) to timing.eight,
            key.note(-1) to timing.eight,
            key.note(0) to timing.eight,
            key.note(4) to timing.quarter,
            key.note(-1) to timing.eight,
            key.note(0) to timing.eight + timing.whole,
            null to timing.eight,
            key.note(0) to timing.eight,
            key.note(-1) to timing.eight,
            key.note(-2) to timing.eight,
            key.note(-3) to timing.quarter,
            key.note(-1) to timing.quarter,
            key.note(0) to timing.eight,
            key.note(-1) to timing.eight,
            key.note(0) to timing.eight,
            key.note(4) to timing.quarter * 1.5,
            key.note(4) to timing.eight,
            key.note(5) to timing.eight,
            key.note(4) to timing.quarter,
            key.note(3) to timing.eight,
            key.note(2) to timing.quarter * 1.5,
            key.note(0) to timing.quarter,
            key.note(1) to timing.quarter * 1.5,
            key.note(-2) to timing.quarter * 1.5,
            key.note(1) to timing.quarter,
            key.note(2) to timing.quarter * 1.5,
            key.note(3) to timing.sixteenth,
            key.note(2) to timing.sixteenth,
            key.note(1) to timing.half,
            null to timing.whole * 3,
        ).forEach { (note, duration) ->
            if(note == null) {
                wait(duration)
            } else {
                play(lead, note, duration, cutoffBuffer = 2.seconds)
            }
        }
    }.gain(0.1).saveAsAudio()
}