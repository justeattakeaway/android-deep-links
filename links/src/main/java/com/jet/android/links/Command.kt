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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KProperty

abstract class Command : CoroutineScope {
    lateinit var supervisor: Job
    override val coroutineContext: CoroutineContext by lazy {
        supervisor + Dispatchers.Main
    }

    lateinit var controller: DeepLinkRouter

    var uri: Uri = Uri.EMPTY

    abstract fun execute()

    /**
     * Called internally when appropriate
     */
    var navigateBlock: (Context) -> Unit = {}

    fun navigate(block: (Context) -> Unit = {}) {
        navigateBlock = block
    }

    inner class QueryParamDelegate(private val name: String = "") {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String? {
            return uri.getQueryParameter(if (name.isNotEmpty()) name else property.name)
        }
    }

    protected fun queryParam(name: String = ""): QueryParamDelegate {
        return QueryParamDelegate(name)
    }

    inner class PathSegmentDelegate(private val index: Int) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String? = when {
            index >= uri.pathSegments.size -> null
            else -> uri.pathSegments[index]
        }
    }

    protected fun pathSegment(index: Int): PathSegmentDelegate {
        return PathSegmentDelegate(index)
    }

    suspend inline fun <reified T : Any> require(): T? {
        return controller.require(this, T::class)
    }
}
