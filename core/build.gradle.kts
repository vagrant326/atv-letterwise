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

/**
 * KSPC over the query corpus, using the shipped disambiguation code. Lives in the test
 * source set so the runner never reaches the APK.
 */
tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Measures KSPC over bench/queries-v1.tsv"
    mainClass.set("io.github.vagrant326.atvletterwise.core.bench.BenchmarkKt")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = rootProject.projectDir
    args(
        "--queries", "bench/queries-v1.tsv",
        "--model-pl", "app/src/main/assets/trigrams-pl.bin",
        "--model-en", "app/src/main/assets/trigrams-en.bin",
    )
}
