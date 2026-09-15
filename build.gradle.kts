/*
 * ============================================================================
 * syzygy-services-android — build.gradle.kts
 * ============================================================================
 *
 * ## Why the Android Gradle Plugin (AGP) is NOT used here
 *
 * This module is deliberately built as a **pure JVM library** using the
 * `org.jetbrains.kotlin.jvm` plugin rather than AGP (`com.android.library`).
 *
 * Reasons:
 * - The Kotlin JVM plugin compiles and tests without an Android SDK or emulator.
 *   Every service in this library is written against Foundation interfaces and
 *   standard JVM APIs (javax.crypto, java.io, OkHttp) so that `./gradlew test`
 *   runs entirely on the host machine in CI with zero Android toolchain setup.
 * - Faster incremental builds — no dexing, no resource merging, no manifest
 *   processing, no aapt2 pass.
 * - JitPack can publish the artefact as a plain JAR, consumed by Android app
 *   modules that already have AGP configured.
 *
 * ## What needs to change when migrating to AGP (tracked for v1.2.0)
 *
 * When AGP is introduced the following changes will be required:
 *
 * 1. **Plugin swap**: replace `id("org.jetbrains.kotlin.jvm")` with
 *    `id("com.android.library")` + `id("org.jetbrains.kotlin.android")`.
 *    Add an `android { ... }` block with `compileSdk`, `minSdk`, and
 *    `namespace` configured.
 *
 * 2. **EncryptedSharedPreferences**: replace the `EncryptedStorageProvider`'s
 *    `javax.crypto` implementation with
 *    `androidx.security.crypto.EncryptedSharedPreferences`, which uses the
 *    Android KeyStore for hardware-backed key management.  Add:
 *    ```
 *    implementation("androidx.security:security-crypto:1.1.0-alpha06")
 *    ```
 *
 * 3. **SharedPreferencesStorageProvider**: replace the in-memory
 *    `ConcurrentHashMap` with a real `android.content.SharedPreferences`
 *    instance obtained via `Context.getSharedPreferences(...)`.  The
 *    `Context` should be injected via the constructor.
 *
 * 4. **Firebase Cloud Messaging (FCM)**: the `StubPushProvider` can be
 *    replaced with (or supplemented by) a real `FirebaseMessagingService`
 *    subclass.  Add:
 *    ```
 *    implementation("com.google.firebase:firebase-messaging:23.x.x")
 *    ```
 *    See the KDoc on `PushProvider` for the full integration checklist.
 *
 * 5. **WebSocket integration tests**: `OkHttpWebSocketProvider` tests that
 *    currently use `MockWebServer` over plain HTTP will continue to work
 *    under AGP.  However, tests requiring an Android `Looper` (e.g. for
 *    `OkHttpClient` internal dispatching in certain configurations) may need
 *    to run as instrumented tests on a device or emulator.
 *
 * 6. **Publishing**: AGP produces an `.aar` artefact instead of a `.jar`.
 *    The `maven-publish` block will need to reference the `release` component
 *    (`from(components["release"])`) rather than `components["java"]`.
 *
 * Migration target: **v1.2.0** — see CHANGELOG for tracking.
 * ============================================================================
 */

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("maven-publish")
}

// Single canonical version source — bump only this value on each release.
val syzygyVersion = "1.1.0"

group = "com.github.Syzygy-Hub"
version = syzygyVersion

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDirs("src/main/kotlin")
    }
}

dependencies {
    // Syzygy Foundation
    implementation("com.github.Syzygy-Hub:syzygy-foundation-android:1.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // OkHttp is the standard JVM/Android HTTP client — approved as a platform
    // networking primitive. MockWebServer is test-only. Both are analogous to
    // URLSession (iOS), fetch (RN), and dart:io HttpClient (Flutter).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Unit tests — JUnit 5
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.0.21")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Coroutines test
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // MockWebServer for NetworkClient tests
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// ---------------------------------------------------------------------------
// Publishing — JitPack
// ---------------------------------------------------------------------------

val mainSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            groupId = "com.github.Syzygy-Hub"
            artifactId = "syzygy-services-android"
            version = syzygyVersion
            artifact(mainSourcesJar)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// ktlint — lint main Kotlin sources via ktlint-cli
// ---------------------------------------------------------------------------

val ktlintCli: Configuration by configurations.creating

dependencies {
    ktlintCli("com.pinterest.ktlint:ktlint-cli:1.0.1")
}

val ktlintCheckSources by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs ktlint against src/main/**/*.kt"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("src/main/**/*.kt")
    workingDir = project.projectDir
}

tasks.named("ktlintCheck") {
    dependsOn(ktlintCheckSources)
}

val ktlintFormatSources by tasks.registering(JavaExec::class) {
    group = "formatting"
    description = "Auto-fixes ktlint violations in src/**/*.kt"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("-F", "src/main/**/*.kt")
    workingDir = project.projectDir
}

tasks.named("ktlintFormat") {
    dependsOn(ktlintFormatSources)
}
