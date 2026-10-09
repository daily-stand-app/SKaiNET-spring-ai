rootProject.name = "SKaiNET-spring-ai"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Auto-provisions the JDK 25 toolchain the modules request.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":spring-ai-skainet")
include(":spring-ai-starter-model-skainet")
include(":samples:chat-app")
