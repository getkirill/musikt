package musikt

import kotlin.time.Duration.Companion.minutes

data class TimeSignature(val beatsPerMeasure: Int, val beatValue: Int) {
    init {
        require(beatsPerMeasure > 0) { "Beats per measure must be greater than 0" }
        require(beatValue > 0) {
            "Beat value must be greater than 0"
        }
    }
}

data class Timing(val bpm: Double, val timeSignature: TimeSignature = TimeSignature(4, 4)) {
    val beatDuration get() = (1/bpm).minutes
    val whole get() = beatDuration * timeSignature.beatValue.toDouble()
    val half get() = whole / 2
    val quarter get() = whole / 4
    val eighth get() = whole / 8
    val sixteenth get() = whole / 16
    operator fun times(d: Double) = beatDuration * d
}

operator fun Double.times(timing: Timing) = timing * this