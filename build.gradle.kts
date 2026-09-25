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
        intellijIdea("2026.1.5")
        testFramework(TestFrameworkType.Platform)

        // Dart is not bundled with IntelliJ IDEA; pull it from Marketplace for PSI APIs.
        plugin("Dart", "509.0.0")
    }
}

intellijPlatform {
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
