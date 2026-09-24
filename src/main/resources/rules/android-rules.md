# Android Project Rules

- This is an Android project built with Gradle and the Android Gradle Plugin.
- Kotlin source files live under module source sets such as `app/src/main/java` or `app/src/main/kotlin`. Unit tests go under `app/src/test`, instrumented tests under `app/src/androidTest`.
- The file's package declaration must match its directory path inside the source set.
- For Jetpack Compose code: prefer modifying the existing composable in place over creating new components. Preserve modifier order, theming tokens, and preview functions unless the specification explicitly changes them.
- Keep changes minimal and scoped to the declared target file. Do not create new files, resources, or theme tokens the spec does not declare.
- Do not touch `AndroidManifest.xml`, Gradle files, or `res/` resources unless the specification explicitly requires it.
