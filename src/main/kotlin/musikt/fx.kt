package musikt

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Sequence<Double>.gain(vol: Double) = gain(generateSequence { vol })
fun Sequence<Double>.gain(vol: Sequence<Double>) = zip(vol) { it, volume -> it * volume }
fun Sequence<Double>.noise(range: ClosedFloatingPointRange<Double>) =
    map { it + Random.nextDouble(range.start, range.endInclusive) }

fun Sequence<Double>.aliasing(step: Double) = map { it.roundToStep(step) }

fun Sequence<Double>.delay(
    duration: Duration,
    sampleRate: Int = 44100
) = generateSequence { 0.0 }.take((duration / (1.0 / sampleRate).seconds).toInt()) + this
fun Sequence<Double>.pitch(){}

fun linear(): (Double, Double, Double) -> Double = { progress, from, to -> from + (to - from) * progress }
fun constant(value: Double): (Double, Double, Double) -> Double = { _, _, _ -> value }
fun easing(ease: (Double) -> Double): (Double, Double, Double) -> Double =
    { progress, from, to -> from + (to - from) * ease(progress) }

fun easeIn(x: Double) = 1 - cos((x * Math.PI) / 2)
fun easeOut(x: Double) = sin((x * Math.PI) / 2)

fun interpolating(
    from: Double = 0.0,
    to: Double = 1.0,
    fn: (Double, Double, Double) -> Double = linear(),
    duration: Duration,
    sampleRate: Int = 44100
): Sequence<Double> = sequence {
    val totalSamples = (duration / (1.0 / sampleRate).seconds).toLong()
    for (step in 0..totalSamples) {
        val progress = if (totalSamples == 0L) 1.0 else step.toDouble() / totalSamples
        yield(fn(progress, from, to))
    }
}

fun attack(
    peak: Double = 1.0,
    fn: (Double, Double, Double) -> Double = linear(),
    duration: Duration,
    sampleRate: Int = 44100
) = interpolating(from = 0.0, to = peak, fn = fn, duration = duration, sampleRate = sampleRate)

fun sustain(
    at: Double = 1.0,
    fn: (Double, Double, Double) -> Double = linear(),
    duration: Duration,
    sampleRate: Int = 44100
) = interpolating(from = at, to = at, fn = fn, duration = duration, sampleRate = sampleRate)

fun release(
    sustain: Double = 1.0,
    fn: (Double, Double, Double) -> Double = linear(),
    duration: Duration,
    sampleRate: Int = 44100
) = interpolating(
    from = sustain,
    to = 0.0,
    fn = fn,
    duration = duration,
    sampleRate = sampleRate
) + generateSequence { 0.0 }

fun envelope(
    peak: Double = 1.0,
    sustain: Double = 1.0,
    attackFn: (Double, Double, Double) -> Double = linear(),
    decayFn: (Double, Double, Double) -> Double = linear(),
    sustainFn: (Double, Double, Double) -> Double = constant(sustain),
    releaseFn: (Double, Double, Double) -> Double = linear(),
    attackDuration: Duration = Duration.ZERO,
    decayDuration: Duration = Duration.ZERO,
    sustainDuration: Duration = Duration.INFINITE,
    releaseDuration: Duration = Duration.ZERO,
    sampleRate: Int = 44100
): Sequence<Double> =
    attack(peak, attackFn, attackDuration, sampleRate) +
            interpolating(peak, sustain, decayFn, decayDuration, sampleRate) +
            sustain(sustain, sustainFn, sustainDuration, sampleRate) +
            release(sustain, releaseFn, releaseDuration, sampleRate)

fun Sequence<Double>.lowPassFilter(
    cutoff: Sequence<Double>,
    sampleRate: Int = 44100
): Sequence<Double> {
    val cutoffIterator = cutoff.iterator()
    val dt = 1.0 / sampleRate

    return sequence {
        val iterator = this@lowPassFilter.iterator()
        if (!iterator.hasNext()) return@sequence

        var previousOutput = iterator.next()
        yield(previousOutput)

        while (iterator.hasNext()) {
            val rc = 1.0 / (2.0 * PI * cutoffIterator.next())
            val alpha = dt / (rc + dt)
            val currentInput = iterator.next()
            val currentOutput = previousOutput + alpha * (currentInput - previousOutput)
            yield(currentOutput)
            previousOutput = currentOutput
        }
    }
}

fun Sequence<Double>.highPassFilter(
    cutoff: Sequence<Double>,
    sampleRate: Int = 44100
): Sequence<Double> {
    val cutoffIterator = cutoff.iterator()

    val dt = 1.0 / sampleRate

    return sequence {
        val iterator = this@highPassFilter.iterator()
        if (!iterator.hasNext()) return@sequence

        var previousInput = iterator.next()
        var previousOutput = 0.0
        yield(previousOutput)

        while (iterator.hasNext()) {
            val rc = 1.0 / (2.0 * PI * cutoffIterator.next())
            val alpha = rc / (rc + dt)
            val currentInput = iterator.next()
            val currentOutput = alpha * previousOutput + alpha * (currentInput - previousInput)
            yield(currentOutput)

            previousInput = currentInput
            previousOutput = currentOutput
        }
    }
}