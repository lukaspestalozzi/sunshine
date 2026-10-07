plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sunshine.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sunshine.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.maplibre.android.sdk)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    ksp(libs.room.compiler)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqlite.bundled)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Room's bundled SQLite driver needs its native library in JVM unit tests. The Android artifact
// carries only Android ABIs, so the host's copy is taken from the desktop JVM artifact (design D5
// of add-offline-regions).
val sqliteNatives: Configuration =
    configurations.create("sqliteNatives") {
        isTransitive = false
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }

dependencies {
    sqliteNatives(libs.sqlite.bundled.jvm)
}

val hostNatives: String =
    run {
        val os = System.getProperty("os.name").lowercase()
        val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
        when {
            os.startsWith("linux") -> if (arm) "linux_arm64" else "linux_x64"
            os.startsWith("mac") -> if (arm) "osx_arm64" else "osx_x64"
            else -> "windows_x64"
        }
    }

val extractSqliteNatives =
    tasks.register<Sync>("extractSqliteNatives") {
        from({ zipTree(sqliteNatives.singleFile) }) { include("natives/$hostNatives/**") }
        into(layout.buildDirectory.dir("sqlite-natives"))
    }

tasks.withType<Test>().configureEach {
    dependsOn(extractSqliteNatives)
    systemProperty(
        "java.library.path",
        layout.buildDirectory
            .dir("sqlite-natives/natives/$hostNatives")
            .get()
            .asFile.path,
    )
}
