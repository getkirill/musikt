package musikt

import kotlin.math.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 44.1khz
 */
const val DEFAULT_SAMPLE_RATE = 44100

fun Double.roundToStep(step: Double): Double {
    if (step == 0.0) return this // Prevent division by zero
    return round(this / step) * step
}

fun easingInterpolate(ease: (Double) -> Double): (Double, Double, Double) -> Double =
    { progress, from, to -> from + (to - from) * ease(progress) }

fun easeIn(x: Double) = 1 - cos((x * Math.PI) / 2)
fun easeOut(x: Double) = sin((x * Math.PI) / 2)

fun linearInterpolate(): (Double, Double, Double) -> Double = { progress, from, to -> from + (to - from) * progress }
fun constantInterpolate(value: Double): (Double, Double, Double) -> Double = { _, _, _ -> value }

fun sine(phase: Double): Double = sin(2.0 * PI * phase)
fun tri(phase: Double): Double = 2 * asin(sin(2.0 * PI * phase)) / PI
fun saw(phase: Double): Double = -phase.mod(2.0) - 1
fun oscilate(
    fn: (Double) -> Double,
    frequency: Sequence<Double> = sequenceOf { 1.0 },
    sampleRate: Int = DEFAULT_SAMPLE_RATE
) = sequence {
    val frequencyIterator = frequency.iterator()
    fun phaseStep(): Double = frequencyIterator.next() / sampleRate;
    var phase = 0.0
    while (true) {
        yield(fn(phase))
        phase = (phase + phaseStep()) % 1.0
    }
}

fun interpolating(
    from: Double = 0.0,
    to: Double = 1.0,
    fn: (Double, Double, Double) -> Double = linearInterpolate(),
    duration: Duration,
    sampleRate: Int = DEFAULT_SAMPLE_RATE
): Sequence<Double> = sequence {
    val totalSamples = (duration / (1.0 / sampleRate).seconds).toLong()
    for (step in 0..totalSamples) {
        val progress = if (totalSamples == 0L) 1.0 else step.toDouble() / totalSamples
        yield(fn(progress, from, to))
    }
}

/**
 * Like [generateSequence], but infinite and can be reused.
 */
fun<T> sequenceOf(block: () -> T) = sequence { while(true) yield(block()) }