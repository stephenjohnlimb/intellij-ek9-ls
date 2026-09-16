import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    // Kotlin + the IntelliJ Platform Gradle Plugin 2.x. Bump versions if Gradle reports a mismatch.
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            IntelliJPlatformType.IntellijIdeaCommunity,
            providers.gradleProperty("platformVersion").get()
        )
        // LSP4IJ provides the LSP + DAP client this plugin builds on (free, Community-compatible).
        plugin("com.redhat.devtools.lsp4ij:" + providers.gradleProperty("lsp4ijVersion").get())
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242" // IntelliJ 2024.2 — LSP4IJ's floor
            untilBuild = provider { null } // open upper bound so it installs on newer IntelliJ
        }
    }
}

kotlin {
    jvmToolchain(21)
}
