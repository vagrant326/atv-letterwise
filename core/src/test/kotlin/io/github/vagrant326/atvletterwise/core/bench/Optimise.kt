package io.github.vagrant326.atvletterwise.core.bench

import io.github.vagrant326.atvletterwise.core.BinaryNgramModel
import io.github.vagrant326.atvletterwise.core.Disambiguator
import io.github.vagrant326.atvletterwise.core.NgramModel
import io.github.vagrant326.atvletterwise.core.Partition
import io.github.vagrant326.atvletterwise.core.PartitionSearch
import io.github.vagrant326.atvletterwise.core.Simulator
import io.github.vagrant326.atvletterwise.core.TrialResult
import java.io.File

/**
 * Searches for a better letter-to-key assignment, and reports what it is worth.
 *
 * The search runs on a sample of the **training** corpus and the result is scored on the
 * **query** corpus. Optimising directly on 200 characters of queries would find a layout
 * tuned to those nineteen strings and nothing else; the number it reported would be a
 * measurement of overfitting.
 *
 * Three layouts are compared. ITU is what the keyboard ships and what a user can recall from
 * a feature phone. The best contiguous split keeps the alphabet in order, so it can still be
 * recalled, and is searched exhaustively. The unconstrained layout is a yardstick only — it
 * scatters letters, so it would have to be read off the screen forever, and it exists to say
 * how much the alphabetical ordering costs.
 *
 * Run with `./gradlew :core:optimise`.
 */
fun main(arguments: Array<String>) {
    val options = arguments.toList().chunked(2)
        .filter { it.size == 2 }
        .associate { it[0].removePrefix("--") to it[1] }

    val language = options["language"] ?: "en"
    val alphabet = when (language) {
        "pl" -> Partition.ITU_PL
        else -> Partition.ITU
    }.symbols.sorted().joinToString("")

    val model = options["model"]?.let { path ->
        File(path).takeIf { it.isFile }?.inputStream()?.use { BinaryNgramModel.read(it) }
    }
    if (model == null) {
        System.err.println("no model given or file missing; nothing to optimise against")
        return
    }

    val trainingChars = options["chars"]?.toIntOrNull() ?: 400_000
    val training = sample(File(options["train"] ?: "corpus/raw/subtitles-$language.txt"), trainingChars)
    if (training.isEmpty()) {
        System.err.println("no training text")
        return
    }

    val queries = readQueries(File(options["queries"] ?: "bench/queries-v1.tsv"), language)
    if (queries.isEmpty()) {
        System.err.println("no queries for $language")
        return
    }

    println()
    println("language $language, alphabet ${alphabet.length} letters")
    println("search on ${training.sumOf { it.length }} characters of training text")
    println("scored on ${queries.sumOf { it.length }} characters of real queries")
    println()

    val search = PartitionSearch(alphabet, model, training)
    val itu = alphabet.let { _ ->
        (if (language == "pl") Partition.ITU_PL else Partition.ITU)
            .groups.entries.sortedBy { it.key }.map { it.value }
    }

    val candidates = linkedMapOf(
        "ITU" to itu,
        "contiguous" to search.bestContiguous(8),
        "scattered" to search.bestUnconstrained(8, restarts = 6),
    )

    println("%-12s %10s %10s %8s".format("layout", "train cost", "query KSPC", "checks"))
    for ((label, groups) in candidates) {
        val partition = Partition(groups.withIndex().associate { (at, letters) -> ('2' + at) to letters })
        val simulator = Simulator(partition, Disambiguator(partition, model))
        val result = queries.fold(TrialResult.EMPTY) { total, query -> total + simulator.run(query) }
        println(
            "%-12s %10d %10.3f %8.3f".format(
                label,
                search.cost(groups),
                result.kspc,
                result.visualCheckRate,
            )
        )
    }
    println()
    for ((label, groups) in candidates) {
        println("$label  ${groups.joinToString(" ")}")
    }
    println()
}

private fun sample(file: File, limit: Int): List<String> {
    if (!file.isFile) {
        return emptyList()
    }
    val lines = ArrayList<String>()
    var taken = 0
    file.bufferedReader().use { reader ->
        while (taken < limit) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) {
                continue
            }
            lines += line
            taken += line.length
        }
    }
    return lines
}

private fun readQueries(file: File, language: String): List<String> {
    if (!file.isFile) {
        return emptyList()
    }
    val typable = (if (language == "pl") Partition.ITU_PL else Partition.ITU).symbols +
        Simulator.DEFAULT_DETERMINISTIC_KEYS.keys
    return file.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .drop(1)
        .mapNotNull { it.split('\t').firstOrNull()?.trim() }
        .filter { it.isNotEmpty() && it.all { character -> character in typable } }
}
