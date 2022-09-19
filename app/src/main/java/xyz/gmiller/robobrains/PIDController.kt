package xyz.gmiller.robobrains

class PIDController {
    var kp = 0.0
    var ki = 0.0
    var kdError = 0.0
    var kdInput = 0.0
    var minimumCycleMs = 1
    var lastTime = 0
    var lastError = 0.0
    var lastInput = 0.0
    var lastDInput = 0.0
    var lastOutput = 0.0
    var outMin = 0.0
    var outMax = 0.0
    var errorIntegral = 0.0
    var pdiSmoothing = 0

    constructor(kp: Double, ki: Double, kdError: Double, kdInput: Double, outMin: Double, outMax: Double) {
        this.kp = kp
        this.ki = ki
        this.kdError = kdError
        this.kdInput = kdInput
        this.outMin = outMin
        this.outMax = outMax
    }

    fun reset() {
        lastTime = 0
        lastError = 0.0
        lastOutput = 0.0
        lastInput = 0.0
    }

    fun update(input: Double, stepoint: Double): Double {
        var output = 0.0
        return output
    }
}