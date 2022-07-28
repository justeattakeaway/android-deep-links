# Deep Linking API

In an Android app we can define URI patterns that map URI's to activities using intent-filters.

To find out more about this you should read this documentation provided by Google
https://developer.android.com/training/app-links

Once we have defined which URI's are handled by our application in our manifest we can then inspect
the URI of incoming link Intent's and decide how to act on them.

The Deep Linking API takes care of handling incoming links by mapping URI patterns to `Command`'s.

These `Command`'s can then be used to launch an activity, show a Fragment, show some other UI or anything 
else you can do in the context of the Activity that handles your deep links.

The approach is inspired by Martin Fowler's Front Controller pattern.

## Adding Dependencies

### Step 1. Add the JitPack repository to your build file

```groovy
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```

### Step 2. Add the dependency

```groovy
dependencies {
  implementation 'com.jet.android:links:1.0.0'
}
```

## Usage Guide

We first must designate an activity that will handle incoming deep links and add the necessary
intent-filters for the activity in our `AndroidManifest.xml` as follows.

```xml

<activity android:name=".examples.simple.ExampleDeepLinkActivity" android:excludeFromRecents="true"
    android:exported="true" android:launchMode="singleTask">
    <intent-filter tools:ignore="AppLinkUrlError">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />

        <data android:scheme="https" />
        <data android:host="simple.site.com" />
    </intent-filter>
</activity>
```

With our intent-filter defined we can then define our routing, for the simplest approach we
use `deepLinkRouter` extension function to do most of the setup for us.

```kotlin
class ExampleDeepLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deepLinkRouter {
            schemes("https")
            hosts("simple.site.com")

            "/home" mapTo { HomeCommand() }
            "/products/[a-zA-Z0-9]*" mapTo { ProductCommand() }
        }.route(intent.data ?: Uri.EMPTY)
    }
}
```

In the example we map paths `/home` to a `HomeCommand` and also a `/product/[a-zA-Z0-9]*` to
a `ProductCommand` respectively.

The path part is what follows the host part of the URI such as `https://simple.site.com/products/123`
and in our mapping we define it as a Regex.

For the `ProductCommand` we map it to a Regex that matches a product ID of the
pattern `[a-zA-Z0-9]*`

### Commands
With our mapping defined and mapped to commands we need to make our commands do something.

Typically a command will just start an activity however it can do more (more on this later).

The following command is for the `/home` path pattern.

```kotlin
class HomeCommand : Command() {
    override fun execute() = navigate { context ->
        context.startActivity(Intent(context, HomeActivity::class.java))
    }
}
```

Looking at the `HomeCommand` example notice the block `navigate { .. }`.

Commands should end with a `navigate {}` block and the block should define what should happen once
the command is complete. The reason for this is commands can work with coroutines (more on this
later)
and sometimes a command may take longer to complete and also go through Android configuration
changes. The `navigate {}` block will be called at a moment where its safe to do so in the android
UI lifecycle and given the current `Context` so that it may safely perform things like intent
navigation.

Other than the lengthy explanation for the navigate block, a command is mostly simple and all it
does is redirect to an activity, in this case `HomeActivity`.

The next example is the `ProductCommand` that is mapped to the pattern `/products/[a-zA-Z0-9]*`

```kotlin
class ProductCommand : Command() {
    private val productId by pathSegment(1)

    override fun execute() = navigate { context ->
        context.startActivity(
            Intent(context, ProductActivity::class.java)
                .putExtra("productId", productId)
        )
    }
}
```

This command extracts a path segment from position 1 in the URI which is the part that matched
`[a-zA-Z0-9]*` giving us the product ID. We achieve this using the convenient `pathSegment(index)`
property delegate.

As with `pathSegment(index)` we can also use `queryParam(name)` to get at the URI's query
parameters, if that is not enough you can access a property `uri` which will give you
the `android.net.Uri`.

The `ProductCommand` concludes with `navigate { }` constructing an `Intent` for `ProductActivity`
passing along the product ID extracted from the Uri as an intent extra.

### Testing your deep links

To test links you can use an ADB shell command to launch your app and give it a link, the following
example shows how to launch with a link that maps to `HomeCommand`.

```shell
adb shell am start -W -a android.intent.action.VIEW -d "https://simple.site.com/home" com.jet.android.links
```

In the command we specify which app to launch using the package name `com.jet.android.links`.

You can read more about this in the official Android developer docs
https://developer.android.com/training/app-links/deep-linking#testing-filters

Similarly to map to `ProductCommand` we can use the URI pattern with the product id as follows.

```shell
adb shell am start -W -a android.intent.action.VIEW -d "https://simple.site.com/products/abcd1234" com.jet.android.links
```

### Command Requirements

Intercepting a deep link and handing it in a command is useful, we can inspect the deep link and
route it into the app to an activity or other. Sometimes however we may need more information from
the user that is deep-linking into the app or we may require them to satisfy a particular state such
as being authenticated or being geo located.

To handle these situations we can use Command Requirements, a neat way to suspend a command until
the requirements are satisfied.

The following example shows how to achieve this using the linking API's `require()`
and `satisfy(Any)` functions.

```kotlin
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
```

In the above command when we hit the line `loginResult = require()` our command will suspend and
wait for the value from `require()`.

To make the command continue we need to tell the router/controller to `satisfy(Any)` the
requirement.

The following example shows a deep link router setup that maps an incoming deep link with the path
pattern `/orders/[a-zA-Z0-9]*` to the `OrderDertailsCommand`. This will match on a deep link such
as `https://requirements.site.com/orders/abcd1234`

```kotlin
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
```

We use the Activity Result API to launch a new activity `LoginActivity`, the user enters their name
and returns back with a Login button. When the user returns back to `ExampleDeepLinkActivity`
we extract the `loginName` argument from the result Intent (the name they entered into the name
field on login screen)
and we then call `router.satisfy(LoginResult(name = loginName))` passing in the login name.

In order to launch the `LoginActivity` we need to tell the router what to do when a command comes
across a requirement

```kotlin
        router.onRequirement(this) {
    if (it == LoginResult::class.java) {
        startLoginActivity()
    }
}
```

We achieve this by calling `onRequirement` and if we have test for the requirement for
a `LoginResult` and if true then start the login activity using `startLoginActivity()` which simply
launches the `LoginActivity`.

## Command Completion
When a command completes, the default behaviour when using `deepLinkRouter` extension function
to set up a router will call the commands `navigate(Context)` function and then call `finish()` 
on the activity.

If you want to do something different you can provide your own command completion callback.

```kotlin
router.onCommandComplete(this) {
    when (it) {
        is DeepLinkRouter.Result.Complete -> {
            it.navigate(this)
            // TODO do something after navigate
            finish()
        }
        is DeepLinkRouter.Result.Cancelled -> {
            // TODO handle command cancellation
        }
    }
}
```

Looking at the example we call `onCommpandComplete(LifecycleOwner, (DeepLinkRouter.Result) -> Unit)` with
a callback that can handle the result and then must call `it.navigate(this)` to execute the commands
navigate function manually. You can then either `finish()` the activity (the usual pattern) or do 
something else.

As well as handling command completion we can also define what happens when the command is cancelled,
this will occur if the commands coroutine `Job` is cancelled.

## References
* Handling Android App Links https://developer.android.com/training/app-links
* Testing Links https://developer.android.com/training/app-links/deep-linking#testing-filters
* Martin Fowler's Front Controller Pattern https://martinfowler.com/eaaCatalog/frontController.html

## LICENSE
Copyright 2022 Just Eat Takeaway

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
