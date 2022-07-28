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
package com.jet.android.links.examples.simple

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.jet.android.links.deepLinkRouter
import com.jet.android.links.examples.shared.commands.HomeCommand
import com.jet.android.links.examples.shared.commands.ProductCommand

/**
 * To test these links use ADB
 *
 * eg:- adb shell am start -W -a android.intent.action.VIEW -d "http://simple.site.com/home" com.jet.android.links
 */
class ExampleDeepLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deepLinkRouter {
            schemes("http|https")
            hosts("simple.site.com")

            "/home" mapTo { HomeCommand() }
            "/products/[a-zA-Z0-9]*" mapTo { ProductCommand() }
        }.route(intent.data ?: Uri.EMPTY)
    }
}
