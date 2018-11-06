package org.ghrobotics.lib.subsystems.drive

import com.ctre.phoenix.motorcontrol.ControlMode
import kotlinx.coroutines.runBlocking
import org.ghrobotics.lib.commands.ConditionCommand
import org.ghrobotics.lib.commands.FalconSubsystem
import org.ghrobotics.lib.mathematics.twodim.control.TrajectoryFollower
import org.ghrobotics.lib.mathematics.twodim.geometry.Pose2dWithCurvature
import org.ghrobotics.lib.mathematics.twodim.geometry.Rectangle2d
import org.ghrobotics.lib.mathematics.twodim.trajectory.types.TimedTrajectory
import org.ghrobotics.lib.mathematics.twodim.trajectory.types.mirror
import org.ghrobotics.lib.mathematics.units.Length
import org.ghrobotics.lib.sensors.AHRSSensor
import org.ghrobotics.lib.utils.BooleanSource
import org.ghrobotics.lib.utils.Source
import org.ghrobotics.lib.utils.map
import org.ghrobotics.lib.wrappers.FalconSRX
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.withSign

abstract class TankDriveSubsystem : FalconSubsystem("Drive Subsystem") {
    abstract val leftMaster: FalconSRX<Length>
    abstract val rightMaster: FalconSRX<Length>

    abstract val ahrsSensor: AHRSSensor
    abstract val trajectoryFollower: TrajectoryFollower

    val localization = TankDriveLocalization()

    private var quickStopAccumulator = 0.0

    override fun lateInit() {
        runBlocking { localization.lateInit(this@TankDriveSubsystem) }
    }

    // Common drive types

    fun arcadeDrive(
        linearPercent: Double,
        rotationPercent: Double
    ) {
        val maxInput = max(linearPercent.absoluteValue, rotationPercent.absoluteValue)
            .withSign(linearPercent)

        val leftMotorOutput: Double
        val rightMotorOutput: Double

        if (linearPercent >= 0.0) {
            // First quadrant, else second quadrant
            if (rotationPercent >= 0.0) {
                leftMotorOutput = maxInput
                rightMotorOutput = linearPercent - rotationPercent
            } else {
                leftMotorOutput = linearPercent + rotationPercent
                rightMotorOutput = maxInput
            }
        } else {
            // Third quadrant, else fourth quadrant
            if (rotationPercent >= 0.0) {
                leftMotorOutput = linearPercent + rotationPercent
                rightMotorOutput = maxInput
            } else {
                leftMotorOutput = maxInput
                rightMotorOutput = linearPercent - rotationPercent
            }
        }

        tankDrive(leftMotorOutput, rightMotorOutput)
    }

    fun curvatureDrive(
        linearPercent: Double,
        curvaturePercent: Double,
        isQuickTurn: Boolean
    ) {
        val angularPower: Double
        val overPower: Boolean

        if (isQuickTurn) {
            if (linearPercent.absoluteValue < kQuickStopThreshold) {
                quickStopAccumulator = (1 - kQuickStopAlpha) * quickStopAccumulator +
                    kQuickStopAlpha * curvaturePercent.coerceIn(-1.0, 1.0) * 2.0
            }
            overPower = true
            angularPower = curvaturePercent
        } else {
            overPower = false
            angularPower = linearPercent.absoluteValue * curvaturePercent - quickStopAccumulator

            when {
                quickStopAccumulator > 1 -> quickStopAccumulator -= 1.0
                quickStopAccumulator < -1 -> quickStopAccumulator += 1.0
                else -> quickStopAccumulator = 0.0
            }
        }

        var leftMotorOutput = linearPercent + angularPower
        var rightMotorOutput = linearPercent - angularPower

        // If rotation is overpowered, reduce both outputs to within acceptable range
        if (overPower) {
            when {
                leftMotorOutput > 1.0 -> {
                    rightMotorOutput -= leftMotorOutput - 1.0
                    leftMotorOutput = 1.0
                }
                rightMotorOutput > 1.0 -> {
                    leftMotorOutput -= rightMotorOutput - 1.0
                    rightMotorOutput = 1.0
                }
                leftMotorOutput < -1.0 -> {
                    rightMotorOutput -= leftMotorOutput + 1.0
                    leftMotorOutput = -1.0
                }
                rightMotorOutput < -1.0 -> {
                    leftMotorOutput -= rightMotorOutput + 1.0
                    rightMotorOutput = -1.0
                }
            }
        }

        // Normalize the wheel speeds
        val maxMagnitude = max(leftMotorOutput.absoluteValue, rightMotorOutput.absoluteValue)
        if (maxMagnitude > 1.0) {
            leftMotorOutput /= maxMagnitude
            rightMotorOutput /= maxMagnitude
        }

        tankDrive(leftMotorOutput, rightMotorOutput)
    }

    fun tankDrive(
        leftPercent: Double,
        rightPercent: Double
    ) {
        leftMaster.set(ControlMode.PercentOutput, leftPercent.coerceIn(-1.0, 1.0))
        rightMaster.set(ControlMode.PercentOutput, rightPercent.coerceIn(-1.0, 1.0))
    }

    // Pre-generated Trajectory Methods

    fun followTrajectory(
        trajectory: TimedTrajectory<Pose2dWithCurvature>
    ) = FollowTrajectoryCommand(this, trajectory)

    fun followTrajectory(
        trajectory: TimedTrajectory<Pose2dWithCurvature>,
        pathMirrored: Boolean = false
    ) = followTrajectory(trajectory.let {
        if (pathMirrored) it.mirror() else it
    })

    fun followTrajectory(
        trajectory: Source<TimedTrajectory<Pose2dWithCurvature>>,
        pathMirrored: Boolean = false
    ) = FollowTrajectoryCommand(this, trajectory.map {
        if (pathMirrored) it.mirror() else it
    })

    fun followTrajectory(
        trajectory: TimedTrajectory<Pose2dWithCurvature>,
        pathMirrored: BooleanSource
    ) = followTrajectory(pathMirrored.map(trajectory.mirror(), trajectory))

    fun followTrajectory(
        trajectory: Source<TimedTrajectory<Pose2dWithCurvature>>,
        pathMirrored: BooleanSource
    ) = followTrajectory(pathMirrored.map(trajectory.map { it.mirror() }, trajectory))

    // Region conditional command methods

    fun withinRegion(region: Rectangle2d) =
        withinRegion(Source(region))

    fun withinRegion(region: Source<Rectangle2d>) =
        ConditionCommand { region().contains(localization.robotPosition.translation) }

    companion object {
        const val kQuickStopThreshold = 0.2
        const val kQuickStopAlpha = 0.1
    }
}