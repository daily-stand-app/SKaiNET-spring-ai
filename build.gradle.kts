plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSpring) apply false
    alias(libs.plugins.springBoot) apply false
}

allprojects {
    group = "sk.ainet.spring"
    version = "0.1.0-SNAPSHOT"
}
