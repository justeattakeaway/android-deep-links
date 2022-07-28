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
package com.jet.android.links.examples.shared.commands

import android.content.Intent
import com.jet.android.links.Command
import com.jet.android.links.examples.shared.LoginResult
import com.jet.android.links.examples.shared.OrderDetailsActivity
import kotlinx.coroutines.launch

class OrderDetailsCommand : Command() {
    private val orderId by pathSegment(1)
    private var loginResult: LoginResult? = null

    override fun execute() {
        launch {
            loginResult = require()
        }

        navigate { context ->
            context.startActivity(
                Intent(context, OrderDetailsActivity::class.java)
                    .putExtra("orderId", orderId)
                    .putExtra("loginName", loginResult!!.name)
            )
        }
    }
}
