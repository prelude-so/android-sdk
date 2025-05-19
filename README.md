# Readme
### Using the Android SDK

The Android SDK allows you to capture certain device signals that will be reported back to Prelude to help fight fraud.

It is provided as a regular Maven artifact that you can use as a normal dependency in your Android application, just add it as an implementation dependency:

```
(Kts)
implementation("so.prelude.android:sdk:0.1.0")

(Groovy)
implementation 'so.prelude.android:sdk:0.1.0'
```

Usage of the SDK is very simple, you just need to configure it with your SDK key (you can find it in your Prelude dashboard) and call a single dispatch function:

**Kotlin**:
```
val prelude = Prelude(Configuration(context = context, sdkKey = "sdk_XXXXXXXXXXXXXXXX"))
val dispatchID = prelude.dispatchSignals()
```

**Java**:
```
Prelude prelude = new Prelude(new Configuration(context, "sdk_XXXXXXXXXXXXXXXX"));
String dispatchId = prelude.dispatchSignals();
```

As context it is recommended to pass the application context but you can pass any Android context and the library will resolve the correct one.

Once you get the dispatch ID you can report it back to your own API to be forwarded in subsequent network calls.

There is no restriction on when to call this API but it is recommended to perform it early in the onboarding process as the signals dispatching is performed in a background task and may take some time.

If you want to track the progress of the signal dispatching you can configure a dispatch listener:

**Kotlin**:
```
prelude.dispatchSignals { status, dispatchId ->
    when (status) {
        DispatchStatusListener.Status.STARTED -> TODO()
        DispatchStatusListener.Status.SUCCESS -> TODO()
        DispatchStatusListener.Status.FAILURE -> TODO()
    }
}
```

**Java**:
```
prelude.dispatchSignals((status, dispatchId) -> {
    if (status == DispatchStatusListener.Status.SUCCESS) {
        // TODO
    }
});
```

This way you can continue the onboarding only when you are sure the signal dispatching is complete with the `SUCCESS` status.

**Proguard**

If you use minification in your application (i.e. `isMinifyEnabled = true` somewhere in your `build.gradle` file), please add the following lines to your Proguard configuration file to avoid runtime issues:

```
-dontwarn java.awt.*
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
```