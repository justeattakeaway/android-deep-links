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

class RoutingScope(
    private val schemes: MutableList<Regex>,
    private val hosts: MutableList<Regex>,
    private val paths: MutableList<Routing>
) {
    infix fun String.mapTo(command: () -> Command) {
        paths.add(Routing(toRegex(), command))
    }

    infix fun Array<String>.mapTo(command: () -> Command) {
        this.forEach {
            paths.add(Routing(it.toRegex(), command))
        }
    }

    fun schemes(vararg schemes: String) {
        this.schemes.addAll(schemes.map { it.toRegex() })
    }

    fun hosts(vararg hosts: String) {
        this.hosts.addAll(hosts.map { it.toRegex() })
    }
}

data class Routing(val pattern: Regex, val command: () -> Command)
