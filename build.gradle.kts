import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Set `localIdePath` (e.g. in ~/.gradle/gradle.properties) to build and test against
        // an installed IDE instead of downloading one. Releases still build against 2026.1.5.
        val localIdePath = providers.gradleProperty("localIdePath").orNull
        if (localIdePath != null) local(localIdePath) else intellijIdea("2026.1.5")
        testFramework(TestFrameworkType.Platform)

        // Dart is not bundled with IntelliJ IDEA; pull it from Marketplace for PSI APIs.
        plugin("Dart", "509.0.0")
    }
}

intellijPlatform {
    // Bytecode instrumentation needs a compiler matching the IDE build, which a local IDE
    // does not ship; the plugin has no .form files, so it is skipped for local builds.
    if (providers.gradleProperty("localIdePath").isPresent) instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            // Minimum is 2026.1.5 (261.27258); no upper bound so 2026.2+ can install it too.
            sinceBuild = "261.27258"
            untilBuild = provider { null }
        }
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}
