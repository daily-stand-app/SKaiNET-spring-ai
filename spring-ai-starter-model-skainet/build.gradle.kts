import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSpring)
    `java-library`
}

description = "Spring Boot auto-configuration for SKaiNET-backed ChatModel / EmbeddingModel beans"

kotlin {
    jvmToolchain(25)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(platform(libs.spring.ai.bom))
    api(platform(libs.skainet.bom))
    api(platform(libs.skainet.tx.bom))

    api(project(":spring-ai-skainet"))
    api(libs.spring.boot.autoconfigure)
    api(libs.spring.ai.client.chat)
    api(libs.spring.ai.autoconfigure.chat.client)
    annotationProcessor(libs.spring.boot.configuration.processor)

    // SKaiNET runtime: engine + GGUF loading + Llama/Qwen networks + chat templates.
    implementation(libs.skainet.lang.core)
    implementation(libs.skainet.backend.api)
    implementation(libs.skainet.backend.cpu)
    implementation(libs.skainet.io.core)
    implementation(libs.skainet.io.gguf)
    implementation(libs.skainet.tx.core)
    implementation(libs.skainet.tx.agent)
    implementation(libs.skainet.tx.providers)
    implementation(libs.skainet.tx.inference.llama)
    implementation(libs.skainet.tx.inference.qwen)
    // Native FFM kernel pack (packed Q4_K/Q6_K/Q8_0 matmuls). Auto-discovered via
    // ServiceLoader; silently falls back to the Panama path when the shared
    // library cannot be loaded. Run with --enable-native-access=ALL-UNNAMED.
    runtimeOnly(libs.skainet.backend.native.cpu)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-modules", "jdk.incubator.vector", "--enable-native-access=ALL-UNNAMED")
}
