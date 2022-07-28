package com.jet.android.links

import android.net.Uri
import com.jet.android.links.DeepLinkRouter.Result.Cancelled
import com.jet.android.links.DeepLinkRouter.Result.Complete
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class DeepLinkRouterMainTest {

    @get:Rule
    val coroutinesTestRule = MainDispatcherRule()

    private lateinit var controller: DeepLinkRouter
    private lateinit var result: DeepLinkRouter.Result

    @Before
    fun setup() {
        controller = DeepLinkRouter {
            schemes("http")
            hosts("www.just-test.com")
            "/forever" mapTo { ForeverTillCancelledCommand() }
            "/require" mapTo { WithRequireCommand() }
        }
    }

    @Test
    fun cancelControllerCancelsItselfAndExecutingCommand() = runTest {
        // Given
        controller.route(Uri.parse("http://www.just-test.com/forever")) {
            result = it
        }

        assertEquals(2, controller.job.children.count())

        // When
        controller.cancel()

        // we have to wait for this supervisor to join before we can check cancellation
        controller.job.join()

        // Then
        assertTrue(controller.job.isCancelled)
        assertTrue(result is Cancelled)
        assertEquals(0, controller.job.children.count())
    }

    @Test
    fun cancelCommandCancelsExecutingCommand() {
        // Given
        controller.route(Uri.parse("http://www.just-test.com/require")) {
            result = it
        }

        assertEquals(2, controller.job.children.count())

        // When
        controller.cancelCommand()

        // Then
        assertTrue(result is Cancelled)
        assertFalse(controller.job.isCancelled)
        assertEquals(0, controller.job.children.count())
    }

    @Test
    fun satisfyCompletesCommand() {
        // Given
        controller.onRequirement = {
            controller.satisfy("Hello, Command")
        }

        // When
        controller.route(Uri.parse("http://www.just-test.com/require")) {
            result = it
        }

        // Then
        assertTrue(result is Complete)
        assertFalse(controller.job.isCancelled)
    }

    inner class ForeverTillCancelledCommand : Command() {
        override fun execute() {
            launch {
                while (isActive) {
                    delay(100)
                }
            }
        }
    }

    inner class WithRequireCommand : Command() {
        private var hello: String? = null

        override fun execute() {
            launch {
                hello = require()
            }
        }
    }
}
