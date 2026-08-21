plugins {
    alias(libs.plugins.kotlin.jvm)
}

// No Android dependencies here, ever. The simulator and the shipped IME must call the
// same disambiguation code, otherwise measured KSPC and shipped KSPC diverge silently.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
