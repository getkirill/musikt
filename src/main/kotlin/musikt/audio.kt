package musikt

import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.*
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.DurationUnit


fun mixSamples(
    samples: Iterable<Double>,
    interpolate: (Double, Double) -> Double = { a, b -> (a + b) / 2 }
) = samples.reduce(interpolate)

fun mixAudio(
    tracks: Iterable<Sequence<Double>>,
    interpolate: (Double, Double) -> Double = { a, b -> (a + b) / 2 }
): Sequence<Double> = sequence {
    val iterators = tracks.map { it.iterator() }

    while (true) {
        val iteratorsWithSamples = iterators.filter { it.hasNext() }
        val hasMoreSamples = iteratorsWithSamples.isNotEmpty()
        val mixedSample = mixSamples(iteratorsWithSamples.map { it.next() }, interpolate)

        if (!hasMoreSamples) {
            break
        }

        yield(mixedSample)
    }
}

fun Sequence<Double>.sampleAudio(duration: Duration, sampleRate: Int = 44100): Sequence<Double> = take(
    (sampleRate * duration.toDouble(
        DurationUnit.SECONDS
    )).toInt()
)

fun Sequence<Double>.playAsAudio(sampleRate: Int = 44100) {
    val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
    val info = DataLine.Info(SourceDataLine::class.java, format)
    val line = AudioSystem.getLine(info) as SourceDataLine

    line.open(format)
    line.start()

    val bufferSize = 1024
    val byteBuffer = ByteArray(bufferSize * 2)

    var byteIdx = 0

    forEach { sample ->
        val scaledSample = (sample.coerceIn(-1.0, 1.0) * 32767.0).roundToInt()

        byteBuffer[byteIdx++] = (scaledSample and 0xFF).toByte()
        byteBuffer[byteIdx++] = ((scaledSample shr 8) and 0xFF).toByte()

        if (byteIdx >= byteBuffer.size) {
            line.write(byteBuffer, 0, byteBuffer.size)
            byteIdx = 0
        }
    }

    if (byteIdx > 0) {
        line.write(byteBuffer, 0, byteIdx)
    }

    line.drain()
    line.close()
}

fun Sequence<Double>.saveAsAudio(sampleRate: Int = 44100) {
    val caller =
        Thread.currentThread().stackTrace
            .last { !it.className.contains("musikt.AudioKt") && !it.className.contains("java.lang.Thread") }
    return saveAsAudio(File("${caller.className}.${caller.methodName}.wav"), sampleRate)
}

fun Sequence<Double>.saveAsAudio(file: File, sampleRate: Int = 44100) {
    val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)

    val byteList = mutableListOf<Byte>()

    forEach { sample ->
        val scaledSample = (sample.coerceIn(-1.0, 1.0) * 32767.0).roundToInt()

        byteList.add((scaledSample and 0xFF).toByte())
        byteList.add(((scaledSample shr 8) and 0xFF).toByte())
    }

    val audioBytes = byteList.toByteArray()

    val byteArrayInputStream = ByteArrayInputStream(audioBytes)
    val frameLength = (audioBytes.size / format.frameSize).toLong()

    AudioInputStream(byteArrayInputStream, format, frameLength).use { audioInputStream ->
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, file)
    }
}