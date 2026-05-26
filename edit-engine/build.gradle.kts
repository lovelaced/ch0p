plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The "editing brain": pure Kotlin, no Android deps, no side effects.
// Takes signal curves + a Preset, returns an EditDecisionList. JVM-unit-testable.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
