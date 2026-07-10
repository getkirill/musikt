package musikt

fun main() {
    val timing = Timing(160.0)
    val key = Note.C4.semitones(8).major()
    val lead = SineWave.fx { gain(0.3) }
    audio {
        track(lead) {
            play(key.note(0), timing.whole)
            rest(timing.eight)
            play(key.note(0), timing.eight)
            play(key.note(-1), timing.eight)
            play(key.note(0), timing.eight)
            play(key.note(4), timing.quarter)
            play(key.note(-1), timing.eight)
            play(key.note(0), timing.eight + timing.whole)
            rest(timing.eight)
            play(key.note(0), timing.eight)
            play(key.note(-1), timing.eight)
            play(key.note(-2), timing.eight)
            play(key.note(-3), timing.quarter)
            play(key.note(-1), timing.quarter)
            play(key.note(0), timing.eight)
            play(key.note(-1), timing.eight)
            play(key.note(0), timing.eight)
            play(key.note(4), timing.quarter * 1.5)
            play(key.note(4), timing.eight)
            play(key.note(5), timing.eight)
            play(key.note(4), timing.quarter)
            play(key.note(3), timing.eight)
            play(key.note(2), timing.quarter * 1.5)
            play(key.note(0), timing.quarter)
            play(key.note(1), timing.quarter * 1.5)
            play(key.note(-2), timing.quarter * 1.5)
            play(key.note(1), timing.quarter)
            play(key.note(2), timing.quarter * 1.5)
            play(key.note(3), timing.sixteenth)
            play(key.note(2), timing.sixteenth)
            play(key.note(1), timing.half)
            rest(timing.whole * 3)
        }
    }.save()
}