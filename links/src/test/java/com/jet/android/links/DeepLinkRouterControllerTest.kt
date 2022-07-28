package com.jet.android.links

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class DeepLinkRouterControllerTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private var deepLinkRouter: DeepLinkRouter = mock()
    private var commandCompleteObserver: Observer<SingleLiveEvent<DeepLinkRouter.Result>> = mock()
    private var completeCallbackCaptor = argumentCaptor<CommandCompleteCallback>()

    private lateinit var viewModel: DeepLinkRouterController

    lateinit var activity: ActivityController<FragmentActivity>

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        viewModel = DeepLinkRouterController.Builder(
            store = activity.get().viewModelStore, deepLinkRouterFactory = { deepLinkRouter }).build()
    }

    @Test
    fun onClearedCancelsController() {
        // Given

        // When
        activity.destroy()

        // Then
        verify(deepLinkRouter).cancel()
    }

    @Test
    fun navigateCallsDeepLinkControllerNavigate() {

        // When
        viewModel.route(Uri.EMPTY)

        // Then
        verify(deepLinkRouter).route(eq(Uri.EMPTY), completeCallbackCaptor.capture())
    }

    @Test
    fun navigateCommandCompletePostsLiveDataResult() {

        // Given
        val result: DeepLinkRouter.Result.Complete = mock()

        viewModel.route(Uri.EMPTY)
        verify(deepLinkRouter).route(eq(Uri.EMPTY), completeCallbackCaptor.capture())

        // When
        viewModel.commandComplete.observeForever(commandCompleteObserver)
        completeCallbackCaptor.firstValue.invoke(result)

        // Then
        verify(commandCompleteObserver).onChanged(SingleLiveEvent(result))
    }

    @Test
    fun satisfyCallsDeepLinkControllerSatisfy() {

        // Given
        val requirement = "Hello"

        // When
        viewModel.satisfy(requirement)

        // Then
        verify(deepLinkRouter).satisfy(requirement)
    }
}
