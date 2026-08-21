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

/**
 * Searches for a better letter-to-key assignment. Searches on training text and scores on the
 * query corpus, so the reported figure is not a measurement of overfitting.
 */
tasks.register<JavaExec>("optimise") {
    group = "verification"
    description = "Compares ITU against searched layouts. Pass -Planguage=pl for Polish."
    mainClass.set("io.github.vagrant326.atvletterwise.core.bench.OptimiseKt")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = rootProject.projectDir
    maxHeapSize = "2g"
    val language = providers.gradleProperty("language").orElse("en")
    argumentProviders.add {
        val chosen = language.get()
        listOf(
            "--language", chosen,
            "--model", "app/src/main/assets/trigrams-$chosen.bin",
            "--train", "corpus/raw/subtitles-$chosen.txt",
            "--queries", "bench/queries-v1.tsv",
        )
    }
}

tasks.register<JavaExec>("optimiseTitles") {
    group = "verification"
    description = "Same search, trained on title text rather than dialogue."
    mainClass.set("io.github.vagrant326.atvletterwise.core.bench.OptimiseKt")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = rootProject.projectDir
    maxHeapSize = "2g"
    val language = providers.gradleProperty("language").orElse("en")
    argumentProviders.add {
        val chosen = language.get()
        listOf(
            "--language", chosen,
            "--model", "app/src/main/assets/trigrams-$chosen.bin",
            "--train", "corpus/raw/titles-$chosen.txt",
            "--queries", "bench/queries-v1.tsv",
        )
    }
}
