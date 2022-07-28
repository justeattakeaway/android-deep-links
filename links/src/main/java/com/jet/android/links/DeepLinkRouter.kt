/*
 * Copyright (C) 2022 Just Eat Takeaway
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jet.android.links

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

class DeepLinkRouter : CoroutineScope {
    constructor(mappings: RoutingScope.() -> Unit = {}) {
        configure(mappings)
    }

    constructor(vararg mappings: RoutingScope.() -> Unit) {
        mappings.forEach {
            configure(it)
        }
    }

    internal val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val logger = Logger(false)

    var enableLogging: Boolean = false
        set(value) {
            logger.enabled = value
            field = value
        }

    private data class Requirement(
        val command: Command,
        val type: KClass<*>,
        val complete: (Any) -> Unit,
        val cancel: () -> Unit,
        val isCancelled: () -> Boolean
    )

    var onRequirement: ((KClass<*>) -> Unit) = {}
    private var currentRequirement: Requirement? = null

    private data class CommandGroup(
        val paths: MutableList<Routing> = mutableListOf(),
        val schemes: MutableList<Regex> = mutableListOf(),
        val hosts: MutableList<Regex> = mutableListOf()
    )

    private val commandGroups: MutableList<CommandGroup> = mutableListOf()
    private var currentCommand: Command? = null

    private fun configure(mappings: RoutingScope.() -> Unit) {
        val group = CommandGroup()
        mappings(RoutingScope(group.schemes, group.hosts, group.paths))

        check(
            group.schemes.size > 0 && group.hosts.size > 0
        ) { "You must provide both scheme and host patterns" }

        commandGroups.add(group)
    }

    fun add(groupIndex: Int, mappings: RoutingScope.() -> Unit) {
        val group = commandGroups[groupIndex]
        mappings(RoutingScope(group.schemes, group.hosts, group.paths))
    }

    /**
     * Takes a [Uri] and executes a corresponding command.
     *
     * If schemes or hosts have been added then there must be at least one match
     * respectively or the command will not be executed.
     *
     * @param uri The uri to handle
     * @param complete a [CommandCompleteCallback] callback with a callback that can operate on [Context] where
     * the given callback represents the [Command.navigate] function.
     * @return true if the [Uri] was handled]
     */
    fun route(uri: Uri, complete: CommandCompleteCallback = {}): Boolean {
        check(
            currentCommand == null
        ) {
            "Cannot run two commands at once, please " +
                    "cancel the current command or wait for it to complete"
        }

        val group = commandGroups
            .firstOrNull { group ->
                group.schemes.any { it.matches(uri.scheme.orEmpty()) } &&
                        group.hosts.any { it.matches(uri.host.orEmpty()) }
            }

        if (group == null) {
            logger.log("No Match", "no matching scheme for $uri")
            return false
        }

        val match = group.paths.firstOrNull { it.pattern.matches(uri.path.orEmpty()) }

        return when (match) {
            null -> {
                logger.log("No Match", "no command mapped for $uri")
                false
            }
            else -> {
                executeCommand(match.command, uri, complete)
                true
            }
        }
    }

    private fun executeCommand(createCommand: () -> Command, navigateUri: Uri, complete: CommandCompleteCallback) {
        currentCommand = createCommand().apply {
            controller = this@DeepLinkRouter
            supervisor = Job(this@DeepLinkRouter.job)
            uri = navigateUri
            logger.log("Execute Command", "${javaClass.simpleName} for $uri")
            execute()
            this@DeepLinkRouter.launch {
                supervisor.children.toList().joinAll()
                currentCommand = null
                if (supervisor.isCancelled) {
                    logger.log("Command Cancelled", this@apply.javaClass.simpleName)
                    complete(Result.Cancelled)
                } else {
                    logger.log("Command Complete", this@apply.javaClass.simpleName)
                    complete(Result.Complete(navigateBlock))
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> require(command: Command, requirement: KClass<T>): T? {
        logger.log("Require", requirement.java.simpleName)

        val deferredRequirement = CompletableDeferred<T>(command.supervisor)
        currentRequirement = Requirement(
            command = command,
            type = requirement,
            complete = { deferredRequirement.complete(it as T) },
            cancel = { deferredRequirement.cancel() },
            isCancelled = { deferredRequirement.isCancelled })

        onRequirement.invoke(requirement)

        return deferredRequirement.await()
    }

    fun satisfy(requirement: Any) {
        when {
            currentRequirement == null -> logger.log("Unexpected Satisfy", "Nothing required")
            currentRequirement!!.isCancelled() -> {
                logger.log("Unexpected Satisfy", "Current requirement was cancelled")
            }
            else -> currentRequirement?.let {
                when {
                    requirement.javaClass == it.type.java -> {
                        logger.log("Satisfy", "${requirement.javaClass.simpleName} was satisfied")

                        it.complete.invoke(requirement)
                        currentRequirement = null
                    }
                    else -> logger.log(
                        "Unexpected Satisfy",
                        "expected ${it.type.java.simpleName} but got ${requirement.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun cancelCommand() {
        if (currentCommand != null) {
            logger.log("Command Cancelling", currentCommand!!.javaClass.simpleName)
            currentCommand!!.cancel()
            currentRequirement = null

        }
    }

    class Logger(var enabled: Boolean) {
        val tag = DeepLinkRouter::class.java.simpleName

        fun log(state: String, message: String = "") {
            if (enabled) Log.d(tag, "[$state] $message")
        }
    }

    sealed class Result {
        data class Complete(val navigate: (Context) -> Unit) : Result()
        object Cancelled : Result()
    }

    fun cancel() {
        job.cancel()
    }
}

typealias CommandCompleteCallback = (result: DeepLinkRouter.Result) -> Unit
