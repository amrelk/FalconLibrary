/*
 * FRC Team 5190
 * Green Hope Falcons
 */

/*
 * Some implementations and algorithms borrowed from:
 * NASA Ames Robotics "The Cheesy Poofs"
 * Team 254
 */


@file:Suppress("unused")

package frc.team5190.lib.mathematics.twodim.trajectory

import frc.team5190.lib.mathematics.twodim.geometry.Pose2d
import frc.team5190.lib.mathematics.twodim.geometry.interfaces.IPose2d
import frc.team5190.lib.mathematics.twodim.geometry.interfaces.State
import frc.team5190.lib.mathematics.twodim.trajectory.view.TrajectoryView
import java.util.*

class Trajectory<S : State<S>> : Iterable<S> {

    private val points: MutableList<TrajectoryPoint<S>>


    val indexView = IndexView()

    private val isEmpty: Boolean
        get() = points.isEmpty()

    val firstState: S
        get() = getState(0)

    val lastState: S
        get() = getState(length - 1)


    constructor() {
        points = ArrayList()
    }

    constructor(states: List<S>) {
        points = ArrayList(states.size)
        for (i in states.indices) {
            points.add(TrajectoryPoint(states[i], i))
        }
    }

    val length get() = points.size

    fun getPoint(index: Int): TrajectoryPoint<S> {
        return points[index]
    }

    fun getState(index: Int): S {
        return getPoint(index).state()
    }

    fun getInterpolated(index: Double): TrajectorySamplePoint<S>? {
        return when {
            isEmpty -> null
            index <= 0.0 -> TrajectorySamplePoint(getPoint(0))
            index >= length - 1 -> TrajectorySamplePoint(getPoint(length - 1))
            else -> {
                val i = Math.floor(index).toInt()
                val frac = index - i
                when {
                    frac <= java.lang.Double.MIN_VALUE -> TrajectorySamplePoint(getPoint(i))
                    frac >= 1.0 - java.lang.Double.MIN_VALUE -> TrajectorySamplePoint(getPoint(i + 1))
                    else -> TrajectorySamplePoint(getState(i).interpolate(getState(i + 1), frac), i, i + 1)
                }
            }
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        for (i in 0 until length) {
            builder.append(i)
            builder.append(": ")
            builder.append(getState(i))
            builder.append(System.lineSeparator())
        }
        return builder.toString()
    }

    override fun iterator(): Iterator<S> = object : Iterator<S> {
        val iterator = points.iterator()
        override fun hasNext() = iterator.hasNext()
        override fun next(): S = iterator.next().state
    }

    inner class IndexView : TrajectoryView<S> {

        override fun sample(interpolant: Double): TrajectorySamplePoint<S> {
            return this@Trajectory.getInterpolated(interpolant)!!
        }

        override val firstInterpolant = 0.0
        override val lastInterpolant get() = Math.max(0.0, (this@Trajectory.length - 1).toDouble())

        override val trajectory get() = this@Trajectory
    }
}

fun <S : IPose2d<S>> Trajectory<S>.mirror(): Trajectory<S> {
    val waypoints = ArrayList<S>(this.length)
    for (i in 0 until this.length) {
        waypoints.add(this.getState(i).mirror)
    }
    return Trajectory(waypoints)
}

fun <S : IPose2d<S>> Trajectory<TimedState<S>>.mirrorTimed(): Trajectory<TimedState<S>> {
    val waypoints = ArrayList<TimedState<S>>(this.length)
    for (i in 0 until this.length) {
        val timedState = this.getState(i)
        waypoints.add(TimedState(timedState.state.mirror, timedState.t, timedState.velocity, timedState.acceleration))
    }
    return Trajectory(waypoints)
}

fun <S : IPose2d<S>> Trajectory<S>.transform(transform: Pose2d): Trajectory<S> {
    val waypoints = ArrayList<S>(this.length)
    for (i in 0 until this.length) {
        waypoints.add(this.getState(i).transformBy(transform))
    }
    return Trajectory(waypoints)
}