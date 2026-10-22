package com.example.ai

import android.content.Context
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

data class Prediction(val label: String, val confidence: Float, val escalate: Boolean)

class ThreatClassifier(context: Context) {
    private val vocab = HashMap<String, Int>()
    private val idf: FloatArray
    private val labels: List<String>
    private val weights: Array<FloatArray>
    private val bias: FloatArray
    private val tokenRegex = Regex("\\b\\w\\w+\\b")

    companion object {
        const val THRESHOLD = 0.75f
        val HARMFUL = setOf(
            "CYBERBULLYING", "GROOMING", "ADULT_CONTENT",
            "SELF_HARM_VIOLENCE", "SCAM_PHISHING"
        )
    }

    init {
        val a = context.assets
        val idfList = ArrayList<Float>()
        a.open("model/vocab.txt").bufferedReader().useLines { lines ->
            lines.forEachIndexed { i, l ->
                vocab[l.substringBeforeLast('\t')] = i
                idfList.add(l.substringAfterLast('\t').toFloat())
            }
        }
        idf = idfList.toFloatArray()
        labels = a.open("model/labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        weights = a.open("model/weights.tsv").bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { row -> row.split('\t').map { it.toFloat() }.toFloatArray() }
            .toTypedArray()
        bias = a.open("model/intercepts.tsv").bufferedReader().readLines()
            .filter { it.isNotBlank() }.map { it.trim().toFloat() }.toFloatArray()
    }

    fun classify(text: String): Prediction {
        val tokens = tokenRegex.findAll(text.lowercase()).map { it.value }.toList()
        val counts = HashMap<Int, Int>()
        fun add(t: String) { vocab[t]?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        tokens.forEach(::add)
        for (i in 0 until tokens.size - 1) add(tokens[i] + " " + tokens[i + 1])

        val vec = HashMap<Int, Float>()
        var sq = 0.0
        for ((i, c) in counts) {
            val v = ((1.0 + ln(c.toDouble())) * idf[i]).toFloat()
            vec[i] = v; sq += v * v
        }
        val norm = sqrt(sq).toFloat()
        if (norm == 0f) return Prediction("UNKNOWN", 0f, true)

        val logits = FloatArray(labels.size) { k ->
            var s = bias[k]
            for ((i, v) in vec) s += (v / norm) * weights[k][i]
            s
        }
        val max = logits.max()
        val exps = logits.map { exp((it - max).toDouble()) }
        val total = exps.sum()
        val best = exps.indices.maxByOrNull { exps[it] }!!
        val conf = (exps[best] / total).toFloat()
        return Prediction(labels[best], conf, conf < THRESHOLD)
    }
}
