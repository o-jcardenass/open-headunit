package com.andrerinas.openheadunit.input

import com.andrerinas.openheadunit.app.ActivityLaunchPolicy.LaunchStrategy

/**
 * What a key bound to an action on this unit does when it fires. [LockedInputPolicy] owns the lock
 * half; the rest is whether the action has anywhere to run.
 */
object BoundActionPolicy {
    enum class Outcome { RUN, HELD_LOCKED, NO_SCREEN, BLOCKED_BACKGROUND }

    /**
     * @param hasScreen      the projection screen is collecting local actions.
     * @param launchStrategy how the system lets this app start an activity now; NOTIFICATION means
     *                       it blocks the start, so the key opens nothing.
     */
    fun decide(action: InputAction, engaged: Boolean, hasScreen: Boolean, launchStrategy: LaunchStrategy): Outcome = when {
        !LockedInputPolicy.allowsBoundAction(engaged, action) -> Outcome.HELD_LOCKED
        action is InputAction.Local && !hasScreen -> Outcome.NO_SCREEN
        action is InputAction.LaunchApp && launchStrategy == LaunchStrategy.NOTIFICATION -> Outcome.BLOCKED_BACKGROUND
        else -> Outcome.RUN
    }
}
