package io.github.vagrant326.atvletterwise.core.bench

import io.github.vagrant326.atvletterwise.core.BinaryNgramModel
import io.github.vagrant326.atvletterwise.core.Disambiguator
import io.github.vagrant326.atvletterwise.core.NgramModel
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.core.Simulator
import io.github.vagrant326.atvletterwise.core.TrialResult
import io.github.vagrant326.atvletterwise.core.UniformModel
import java.io.File

/**
 * Measures KSPC over the query corpus, using the same disambiguation code the keyboard runs.
 *
 * Reports the trained model against [UniformModel] side by side. The uniform figure is what
 * the keyboard costs with no prediction at all — a phone keypad — so the gap between the two
 * columns is the only thing that says whether the language model earns its place.
 *
 * Lives in the test source set so it stays out of the APK. Run it with `./gradlew :core:bench`.
 */
private data class Query(val text: String, val language: String, val note: String)

private class Report(val label: String) {
    var trained: TrialResult = TrialResult.EMPTY
    var uniform: TrialResult = TrialResult.EMPTY
    val worst = ArrayList<Pair<String, Double>>()
}

fun main(arguments: Array<String>) {
    val options = arguments.toList().chunked(2)
        .filter { it.size == 2 }
        .associate { it[0].removePrefix("--") to it[1] }

    val queryFile = File(options["queries"] ?: "bench/queries-v1.tsv")
    val queries = read(queryFile)
    if (queries.isEmpty()) {
        System.err.println("no queries in ${queryFile.path}")
        return
    }

    val models = mapOf(
        "pl" to load(options["model-pl"], Partition.ITU_PL),
        "en" to load(options["model-en"], Partition.ITU),
    )

    val reports = linkedMapOf(
        "pl" to Report("Polish"),
        "en" to Report("English"),
    )

    var untypable = 0
    for (query in queries) {
        // A query carrying Polish letters has to be typed on the Polish layout whatever the
        // row says, and "piątek the series" is exactly that case.
        val language = if (query.text.any { it !in EN_TYPABLE }) "pl" else query.language
        val target = if (language == "pl") "pl" else "en"
        val (partition, trainedModel) = models.getValue(target)
        val report = reports.getValue(target)

        val trained = Simulator(partition, Disambiguator(partition, trainedModel))
        val uniform = Simulator(partition, Disambiguator(partition, UniformModel))

        val outcome = runCatching { trained.run(query.text) to uniform.run(query.text) }
        if (outcome.isFailure) {
            System.err.println("untypable: ${query.text}  (${outcome.exceptionOrNull()?.message})")
            untypable++
            continue
        }
        val (withModel, withoutModel) = outcome.getOrThrow()
        report.trained += withModel
        report.uniform += withoutModel
        report.worst += query.text to withModel.kspc
    }

    println()
    println("queries ${queries.size}, untypable $untypable, corpus ${queryFile.path}")
    println()
    println("%-10s %8s %8s %8s %8s %8s".format("set", "chars", "KSPC", "uniform", "gain", "checks"))
    var total = TrialResult.EMPTY
    var totalUniform = TrialResult.EMPTY
    for (report in reports.values) {
        if (report.trained.characters == 0) {
            continue
        }
        total += report.trained
        totalUniform += report.uniform
        line(report.label, report.trained, report.uniform)
    }
    if (total.characters > 0) {
        line("all", total, totalUniform)
    }

    println()
    println("most expensive queries")
    reports.values.flatMap { it.worst }
        .sortedByDescending { it.second }
        .take(5)
        .forEach { (text, kspc) -> println("  %.3f  %s".format(kspc, text)) }
    println()
}

private fun line(label: String, trained: TrialResult, uniform: TrialResult) {
    val gain = if (trained.kspc == 0.0) 0.0 else (uniform.kspc - trained.kspc) / uniform.kspc * 100
    println(
        "%-10s %8d %8.3f %8.3f %7.0f%% %8.3f".format(
            label,
            trained.characters,
            trained.kspc,
            uniform.kspc,
            gain,
            trained.visualCheckRate,
        )
    )
}

private fun load(path: String?, partition: Partition): Pair<Partition, NgramModel> {
    if (path == null) {
        return partition to UniformModel
    }
    val file = File(path)
    if (!file.isFile) {
        System.err.println("no model at $path, using uniform")
        return partition to UniformModel
    }
    return partition to file.inputStream().use { BinaryNgramModel.read(it) }
}

private val EN_TYPABLE: Set<Char> =
    (Partition.ITU.symbols + Simulator.DEFAULT_DETERMINISTIC_KEYS.keys)

private fun read(file: File): List<Query> {
    if (!file.isFile) {
        System.err.println("no query file at ${file.path}")
        return emptyList()
    }
    return file.readLines()
        .asSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .drop(1) // header
        .map { it.split('\t') }
        .filter { it.size >= 2 }
        .map { Query(it[0].trim(), it[1].trim(), it.getOrElse(2) { "" }.trim()) }
        .filter { it.text.isNotEmpty() }
        .toList()
}
