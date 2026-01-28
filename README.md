# Readme
### Usage

The Android SDK allows you to capture certain device signals that will be reported back to Prelude to help fight fraud. It will also allow you to perform silent verification of mobile devices.

It is provided as a regular Maven artifact that you can use as a normal dependency in your Android application, just add it as an implementation dependency:

```
(Kts)
implementation("so.prelude.android:sdk:0.3.0")

(Groovy)
implementation 'so.prelude.android:sdk:0.3.0'
```

***Important: To use the SDK you will need the SDK key that you generate in the [Prelude dashboard](https://app.prelude.so/) for your account. When it is created, you will be able to copy it and it should be stored in a safe location for later use, as the dashboard will only show the SDK key once right after it is created. If you lose the key you will need to generate a new one for future use.***

#### Capturing Signals

To capture device signals you just need to configure it with your SDK key and call a single dispatch function:

**Kotlin**:
```
coroutineScope.launch {
  val prelude = Prelude(Configuration(context = context, sdkKey = "sdk_XXXXXXXXXXXXXXXX"))
  val dispatchId: String? = prelude.dispatchSignals().getOrNull()
  ...
  // Use the dispatchId to report it back to your API
}
```

**Java**:
```
Prelude prelude = new Prelude(new Configuration(context, "sdk_XXXXXXXXXXXXXXXX"));
prelude.dispatchSignals((status, dispatchId) -> {
    if (status == DispatchStatusListener.Status.SUCCESS) {
        // TODO
    }
});
```

The `dispatchSignals` function will capture the device signals and report them to Prelude. It will return a `dispatchId` string that you should report back to your back-end to enhance the phone number verification process.

As context it is recommended to pass the application context but you can pass any Android context and the library will resolve the correct one.

There is no restriction on when to call this API but it is recommended to perform it early in the onboarding process. The recommended way of integrating it is to call the `dispatchSignals` function before displaying the phone number verification screen in your application. This way you can ensure that the device signals are captured and the `dispatchId` can be sent to your back-end with the phone number. Your back-end will then perform the verification call to Prelude with the phone number and the dispatch identifier.

This way you can continue the onboarding only when you are sure the signal dispatching is successful.

#### Silent Verification

The Silent Verification feature allows you to verify a phone number without requiring the user to manually enter a verification code.

It is available for certain carriers and requires a server-side service to handle the verification process. For this verification method to work properly, you *must* collect the device signals mentioned before and report the dispatch identifier to your back-end (usually in your APIs verification endpoint).

Please refer to the [Silent Verification documentation](https://docs.prelude.so/verify/v2/documentation/silent-verification) for more information on how to implement this feature.

#### Proguard

If you use minification in your application (i.e. `isMinifyEnabled = true` somewhere in your `build.gradle` file), the SDK automatically provides rules that will be integrated into your project.

If you find any Proguard runtime issues, these are the required rules for JNA:

```
-dontwarn java.awt.*
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
```
