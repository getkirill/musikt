package musikt

import kotlin.math.PI
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Produces a sequence of doubles equal to current value.
 */
class Knob(var value: Double = 1.0) : Sequence<Double> {
    val interpolatedValue get() = value
    override fun iterator(): Iterator<Double> = sequence {
        while (true) yield(interpolatedValue)
    }.iterator()
}
fun Sequence<Double>.gain(vol: Double) = gain(sequenceOf { vol })
fun Sequence<Double>.gain(vol: Sequence<Double>) = zip(vol) { it, volume -> it * volume }
fun Instrument.gain(vol: Double) = gain(sequenceOf { vol })
fun Instrument.gain(vol: Sequence<Double>) = fx { gain(vol) }
fun Sequence<Double>.noise(range: ClosedFloatingPointRange<Double>) =
    map { it + Random.nextDouble(range.start, range.endInclusive) }

fun Sequence<Double>.aliasing(step: Double) = map { it.roundToStep(step) }

fun Sequence<Double>.delay(
    duration: Duration,
    sampleRate: Int = DEFAULT_SAMPLE_RATE
) = sequenceOf { 0.0 }.take((duration / (1.0 / sampleRate).seconds).toInt()) + this


fun attack(
    peak: Double = 1.0,
    fn: (Double, Double, Double) -> Double = linearInterpolate(),
    duration: Duration,
    sampleRate: Int = DEFAULT_SAMPLE_RATE
) = interpolating(from = 0.0, to = peak, fn = fn, duration = duration, sampleRate = sampleRate)

fun sustain(
    at: Double = 1.0,
    fn: (Double, Double, Double) -> Double = linearInterpolate(),
    duration: Duration,
    sampleRate: Int = DEFAULT_SAMPLE_RATE
) = interpolating(from = at, to = at, fn = fn, duration = duration, sampleRate = sampleRate)

fun release(
    sustain: Double = 1.0,
    fn: (Double, Double, Double) -> Double = linearInterpolate(),
    duration: Duration,
    sampleRate: Int = DEFAULT_SAMPLE_RATE
) = interpolating(
    from = sustain,
    to = 0.0,
    fn = fn,
    duration = duration,
    sampleRate = sampleRate
) + sequenceOf { 0.0 }

fun envelope(
    peak: Double = 1.0,
    sustain: Double = 1.0,
    attackFn: (Double, Double, Double) -> Double = linearInterpolate(),
    decayFn: (Double, Double, Double) -> Double = linearInterpolate(),
    sustainFn: (Double, Double, Double) -> Double = constantInterpolate(sustain),
    releaseFn: (Double, Double, Double) -> Double = linearInterpolate(),
    attackDuration: Duration = Duration.ZERO,
    decayDuration: Duration = Duration.ZERO,
    sustainDuration: Duration = Duration.INFINITE,
    releaseDuration: Duration = Duration.ZERO,
    sampleRate: Int = DEFAULT_SAMPLE_RATE
): Sequence<Double> =
    attack(peak, attackFn, attackDuration, sampleRate) +
            interpolating(peak, sustain, decayFn, decayDuration, sampleRate) +
            sustain(sustain, sustainFn, sustainDuration, sampleRate) +
            release(sustain, releaseFn, releaseDuration, sampleRate)

fun Sequence<Double>.lowPassFilter(
    cutoff: Sequence<Double>,
    sampleRate: Int = DEFAULT_SAMPLE_RATE
): Sequence<Double> = sequence {
    val sampleIterator = this@lowPassFilter.iterator()
    val cutoffIterator = cutoff.iterator()

    var prevOutput = 0.0

    while (sampleIterator.hasNext() && cutoffIterator.hasNext()) {
        val x = sampleIterator.next()
        val fc = cutoffIterator.next()

        val dt = 1.0 / sampleRate
        val rc = 1.0 / (2.0 * PI * fc)
        val alpha = dt / (rc + dt)

        val currentOutput = prevOutput + alpha * (x - prevOutput)

        yield(currentOutput)
        prevOutput = currentOutput
    }
}

fun Sequence<Double>.highPassFilter(
    cutoff: Sequence<Double>,
    sampleRate: Int = DEFAULT_SAMPLE_RATE
): Sequence<Double> = sequence {
    val sampleIterator = this@highPassFilter.iterator()
    val cutoffIterator = cutoff.iterator()

    var prevInput = 0.0
    var prevOutput = 0.0

    while (sampleIterator.hasNext() && cutoffIterator.hasNext()) {
        val x = sampleIterator.next()
        val fc = cutoffIterator.next()

        val dt = 1.0 / sampleRate
        val rc = 1.0 / (2.0 * PI * fc)
        val alpha = rc / (rc + dt)

        val currentOutput = alpha * (prevOutput + x - prevInput)

        yield(currentOutput)
        prevInput = x
        prevOutput = currentOutput
    }
}