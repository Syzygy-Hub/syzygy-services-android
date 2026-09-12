plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("maven-publish")
}

// Single canonical version source — bump only this value on each release.
val syzygyVersion = "1.0.0"

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
