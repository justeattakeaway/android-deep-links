package com.jet.android.links

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

class DeepLinkRouterController(deepLinkRouterFactory: () -> DeepLinkRouter) : ViewModel() {

    private val deepLinkRouter = deepLinkRouterFactory()

    init {
        deepLinkRouter.onRequirement = { requirement ->
            requirements.value = SingleLiveEvent(requirement.java)
        }
    }

    internal val commandComplete = MutableLiveData<SingleLiveEvent<DeepLinkRouter.Result>>()
    internal val requirements = MutableLiveData<SingleLiveEvent<Class<*>>>()

    fun onCommandComplete(owner: LifecycleOwner, block: (DeepLinkRouter.Result) -> Unit) {
        commandComplete.observe(owner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                block(result)
            }
        }
    }

    fun onRequirement(owner: LifecycleOwner, block: (Class<*>) -> Unit) : DeepLinkRouterController{
        requirements.observe(owner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                block(result)
            }
        }
        return this
    }

    var cachedUri: Uri? = null

    fun route(uri: Uri): Boolean {
        return if (cachedUri == uri) {
            false
        } else {
            cachedUri = uri
            deepLinkRouter.route(uri) {
                commandComplete.value = SingleLiveEvent(it)
            }
        }
    }

    fun satisfy(requirement: Any) {
        deepLinkRouter.satisfy(requirement)
    }

    override fun onCleared() {
        deepLinkRouter.cancel()
    }

    fun cancelCommand() {
        cachedUri = null
        deepLinkRouter.cancelCommand()
    }

    fun enableLogging(enabled: Boolean) {
        deepLinkRouter.enableLogging = enabled
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val deepLinkRouterFactory: () -> DeepLinkRouter) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeepLinkRouterController(deepLinkRouterFactory) as T
        }
    }

    class Builder {
        val provider: ViewModelProvider
        val factory: Factory
        var logging: Boolean = false

        constructor(store: ViewModelStore, vararg mappings: RoutingScope.() -> Unit) {
            factory = Factory { DeepLinkRouter(*mappings) }
            provider = ViewModelProvider(store, factory)
        }

        constructor(store: ViewModelStore, mappings: RoutingScope.() -> Unit = {}) {
            factory = Factory { DeepLinkRouter(mappings) }
            provider = ViewModelProvider(store, factory)
        }

        constructor(store: ViewModelStore, deepLinkRouterFactory: () -> DeepLinkRouter) {
            factory = Factory(deepLinkRouterFactory)
            provider = ViewModelProvider(store, factory)
        }


        fun enableLogging(enabled: Boolean): Builder {
            logging = enabled
            return this
        }

        fun build(): DeepLinkRouterController = provider[DeepLinkRouterController::class.java].apply {
            enableLogging(logging)
        }
    }
}

fun ComponentActivity.deepLinkRouter(
    commandCompleteCallback: (DeepLinkRouter.Result) -> Unit = simpleCommandCompleteCallback(this),
    requirementsCallback: ((Class<*>) -> Unit)? = null,
    mappings: RoutingScope.() -> Unit
): DeepLinkRouterController =
    DeepLinkRouterController.Builder(viewModelStore, mappings).build().apply {
        onCommandComplete(this@deepLinkRouter, commandCompleteCallback)
        requirementsCallback?.let {
            onRequirement(this@deepLinkRouter, it)
        }
    }

fun ComponentActivity.deepLinkRouter(
    commandCompleteCallback: (DeepLinkRouter.Result) -> Unit = simpleCommandCompleteCallback(this),
    requirementsCallback: ((Class<*>) -> Unit)? = null,
    vararg mappings: RoutingScope.() -> Unit
): DeepLinkRouterController =
    DeepLinkRouterController.Builder(viewModelStore, *mappings).build().apply {
        onCommandComplete(this@deepLinkRouter, commandCompleteCallback)
        requirementsCallback?.let {
            onRequirement(this@deepLinkRouter, it)
        }
    }

private fun simpleCommandCompleteCallback(activity: ComponentActivity): (DeepLinkRouter.Result) -> Unit =
    { result ->
        when (result) {
            is DeepLinkRouter.Result.Complete -> {
                result.navigate(activity)
                activity.finish()
            }
            is DeepLinkRouter.Result.Cancelled -> {}
        }
    }

internal data class SingleLiveEvent<out T>(private val content: T) {
    var hasBeenHandled = false
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    fun peekContent(): T = content
}
