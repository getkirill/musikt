package musikt

import kotlin.math.pow

val Double.semitones get() = 2.0.pow(this / 12.0)
val Double.octaves get() = 2.0.pow(this)

data class Note(val frequency: Double) {
    fun semitones(d: Double) = Note(frequency * d.semitones)
    fun semitones(i: Int) = semitones(i.toDouble())
    fun octaves(d: Double) = Note(frequency * d.octaves)
    fun octabes(i: Int) = octaves(i.toDouble())
    fun play(instrument: Instrument, sampleRate: Int = 44100) = instrument.sample(frequency, sampleRate)

    override fun toString(): String {
        return "muskt.Note(frequency=$frequency)"
    }

    companion object {
        val C4 = Note(440.0)
    }
}

data class Octave(val frequency: Double) {
    fun shift(by: Int) = Octave(frequency * by.toDouble().octaves)
    val c = Note(frequency)
    val cSharp = c.semitones(1)
    val d = c.semitones(2)
    val dSharp = c.semitones(3)
    val e = c.semitones(4)
    val f = c.semitones(5)
    val fSharp = c.semitones(6)
    val g = c.semitones(7)
    val gSharp = c.semitones(8)
    val a = c.semitones(9)
    val aSharp = c.semitones(10)
    val b = c.semitones(11)

    companion object {
        val C4 = Octave(440.0)
    }
}

data class Key(val root: Note, val semitoneProgression: List<Int>) {
    fun note(offset: Int) = if (offset >= 0) {
        root.semitones((0..<offset).sumOf { semitoneProgression[it % semitoneProgression.size] })
    } else {
        root.semitones(-(offset..<0).sumOf {
            val index = (it % semitoneProgression.size + semitoneProgression.size) % semitoneProgression.size
            semitoneProgression[index]
        })
    }
}

fun Note.major() = Key(this, listOf(2, 2, 1, 2, 2, 2, 1))