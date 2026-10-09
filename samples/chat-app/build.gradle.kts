import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSpring)
    alias(libs.plugins.springBoot)
}

kotlin {
    jvmToolchain(25)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))
    implementation(project(":spring-ai-starter-model-skainet"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.ai.client.chat)
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs("--add-modules", "jdk.incubator.vector", "--enable-native-access=ALL-UNNAMED", "-Xmx8g")
}
