package teamcode.robot.core.utils

fun convertTicksToRevolutions(ticks: Double, ticksPerRevolution: Double): Double {
    return ticks / ticksPerRevolution
}

fun convertRevolutionsToTicks(revolutions: Double, ticksPerRevolution: Double): Double {
    return revolutions * ticksPerRevolution
}

fun degreesToRevolutions(degrees: Double): Double {
    return degrees / 360.0
}

fun degreesToTicks(degrees: Double, ticksPerRevolution: Double): Double {
    val revolutions = degreesToRevolutions(degrees)
    return convertRevolutionsToTicks(revolutions, ticksPerRevolution)
}

fun ticksToDegrees(ticks: Double, ticksPerRevolution: Double): Double {
    val revolutions = convertTicksToRevolutions(ticks, ticksPerRevolution)
    return revolutions * 360.0
}

