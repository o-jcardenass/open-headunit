package com.andrerinas.openheadunit.input

import com.andrerinas.openheadunit.app.ActivityLaunchPolicy.LaunchStrategy
import com.andrerinas.openheadunit.input.BoundActionPolicy.Outcome
import com.andrerinas.openheadunit.main.FavoriteTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundActionPolicyTest {
    private val launch = InputAction.LaunchApp(FavoriteTarget.App("com.example"))
    private val dim = InputAction.Local(LocalAction.DIM_SCREEN)

    private fun decide(
        action: InputAction,
        engaged: Boolean = false,
        hasScreen: Boolean = true,
        strategy: LaunchStrategy = LaunchStrategy.DIRECT,
    ) = BoundActionPolicy.decide(action, engaged, hasScreen, strategy)

    @Test fun anActionWithEverythingInPlaceRuns() {
        assertEquals(Outcome.RUN, decide(launch))
        assertEquals(Outcome.RUN, decide(dim))
        assertEquals(Outcome.RUN, decide(launch, strategy = LaunchStrategy.OVERLAY))
    }

    @Test fun aLockedUnitHoldsAnAppLaunchBeforeTheBackgroundBlock() {
        assertEquals(Outcome.HELD_LOCKED, decide(launch, engaged = true, strategy = LaunchStrategy.NOTIFICATION))
        assertEquals(Outcome.RUN, decide(dim, engaged = true))
    }

    @Test fun aLocalActionWithNoProjectionScreenHasNowhereToRun() {
        assertEquals(Outcome.NO_SCREEN, decide(dim, hasScreen = false))
        assertEquals(Outcome.RUN, decide(launch, hasScreen = false))
    }

    @Test fun anAppLaunchTheSystemBlocksFromTheBackgroundOpensNothing() {
        assertEquals(Outcome.BLOCKED_BACKGROUND, decide(launch, strategy = LaunchStrategy.NOTIFICATION))
    }
}
