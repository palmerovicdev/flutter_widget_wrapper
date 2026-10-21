import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.changelog.Changelog

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Set `localIdePath` (e.g. in ~/.gradle/gradle.properties) to build and test against an
// installed IDE instead of downloading one. Releases build against 2026.1.5: pass an empty
// `-PlocalIdePath=` to override a user-level setting.
val localIdePath: String? = providers.gradleProperty("localIdePath").orNull?.takeIf { it.isNotBlank() }

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    // The IntelliJ Platform Settings plugin disables Kotlin's automatic stdlib
    // dependency so the IDE-provided version is used at runtime. Keep it on the
    // compile classpath so kotlinc can resolve Kotlin built-ins.
    compileOnly(kotlin("stdlib"))

    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        if (localIdePath != null) local(localIdePath) else intellijIdea("2026.1.5")
        testFramework(TestFrameworkType.Platform)

        // Dart is not bundled with IntelliJ IDEA; pull it from Marketplace for PSI APIs.
        plugin("Dart", "509.0.0")
    }
}

intellijPlatform {
    // Bytecode instrumentation needs a compiler matching the IDE build, which a local IDE
    // does not ship; the plugin has no .form files, so it is skipped for local builds.
    if (localIdePath != null) instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            // Minimum is 2026.1.5 (261.27258); no upper bound so 2026.2+ can install it too.
            sinceBuild = "261.27258"
            untilBuild = provider { null }
        }
    }
}

tasks {
    patchPluginXml {
        // Notes of the version being built; Unreleased while it has no section of its own.
        val pluginVersion = project.version.toString()
        changeNotes.set(provider {
            changelog.renderItem(
                (changelog.getOrNull(pluginVersion) ?: changelog.getUnreleased())
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML
            )
        })
    }

    buildSearchableOptions {
        enabled = false
    }
}
