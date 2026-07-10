package musikt

import kotlin.math.round

fun Double.roundToStep(step: Double): Double {
    if (step == 0.0) return this // Prevent division by zero
    return round(this / step) * step
}