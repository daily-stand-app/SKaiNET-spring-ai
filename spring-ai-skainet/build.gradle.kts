import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
}

description = "Spring AI ChatModel / EmbeddingModel adapter over the SKaiNET-transformers neutral LLM SPI"

kotlin {
    jvmToolchain(25)
    explicitApi()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

dependencies {
    api(platform(libs.spring.ai.bom))
    api(platform(libs.skainet.tx.bom))

    // The ONLY SKaiNET dependency of the adapter: the framework-neutral SPI.
    api(libs.skainet.tx.api)
    api(libs.spring.ai.model)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(libs.kotlin.test.junit5)
}

tasks.test { useJUnitPlatform() }
