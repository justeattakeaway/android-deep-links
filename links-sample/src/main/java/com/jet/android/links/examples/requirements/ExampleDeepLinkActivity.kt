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
package com.jet.android.links.examples.requirements

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.jet.android.links.deepLinkRouter
import com.jet.android.links.examples.shared.LoginActivity
import com.jet.android.links.examples.shared.LoginResult
import com.jet.android.links.examples.shared.commands.HomeCommand
import com.jet.android.links.examples.shared.commands.OrderDetailsCommand

/**
 * To test these links use ADB
 *
 * eg:- adb shell am start -W -a android.intent.action.VIEW -d "https://requirements.site.com/orders/abcd1234" com.jet.android.links
 */
class ExampleDeepLinkActivity : ComponentActivity() {
    private val router by lazy {
        deepLinkRouter {
            schemes("https")
            hosts("requirements.site.com")

            "/home" mapTo { HomeCommand() }
            "/orders/[a-zA-Z0-9]*" mapTo { OrderDetailsCommand() }
        }
    }

    private val loginForResult = registerForActivityResult(StartActivityForResult()) {
        val loginName = it.data!!.getStringExtra("loginName")!!
        router.satisfy(LoginResult(name = loginName))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        router.onRequirement(this) {
            if (it == LoginResult::class.java) {
                startLoginActivity()
            }
        }

        router.route(intent.data ?: Uri.EMPTY)
    }

    private fun startLoginActivity() {
        loginForResult.launch(Intent(this, LoginActivity::class.java))
    }
}
