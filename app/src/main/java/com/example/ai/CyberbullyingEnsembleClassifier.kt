package com.example.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.regex.Pattern

data class Model2Prediction(
    val label: String,
    val confidence: Float,
    val isCyberbullying: Boolean,
    val probabilities: Map<String, Float> = emptyMap()
)

class CyberbullyingEnsembleClassifier(context: Context) {

    private val vocab = HashMap<String, Int>()
    private val labels: List<String>
    private var interpreter: Interpreter? = null
    private var maxLen: Int = 50
    private var padId: Int = 0
    private var unkId: Int = 1

    private val urlPattern = Pattern.compile("https?://\\S+|www\\.\\S+")
    private val mentionPattern = Pattern.compile("@\\w+")
    private val rtPattern = Pattern.compile("\\brt\\b")
    private val nonAlnumPattern = Pattern.compile("[^a-z0-9\\s]")
    private val repeatPattern = Pattern.compile("(.)\\1{2,}")

    init {
        val assets = context.assets

        // 1. Load config.json
        try {
            val configStr = assets.open("model2/config.json").bufferedReader().use { it.readText() }
            val configObj = JSONObject(configStr)
            maxLen = configObj.optInt("max_len", 50)
            padId = configObj.optInt("pad_id", 0)
            unkId = configObj.optInt("unk_id", 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Load vocab.json
        try {
            val vocabStr = assets.open("model2/vocab.json").bufferedReader().use { it.readText() }
            val vocabObj = JSONObject(vocabStr)
            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocab[key] = vocabObj.getInt(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Load labels.json
        val labelsList = mutableListOf<String>()
        try {
            val labelsStr = assets.open("model2/labels.json").bufferedReader().use { it.readText() }
            val labelsArr = JSONArray(labelsStr)
            for (i in 0 until labelsArr.length()) {
                labelsList.add(labelsArr.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        labels = if (labelsList.isNotEmpty()) labelsList else listOf("age", "ethnicity", "gender", "not_cyberbullying", "religion")

        // 4. Load TFLite Model
        try {
            val modelBuffer = loadModelFile(context, "model2/cyberbullying_ensemble.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Throwable) {
            e.printStackTrace()
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun cleanText(text: String): String {
        var t = text.lowercase()
        t = urlPattern.matcher(t).replaceAll(" ")
        t = mentionPattern.matcher(t).replaceAll(" ")
        t = t.replace("#", " ")
        t = rtPattern.matcher(t).replaceAll(" ")
        t = nonAlnumPattern.matcher(t).replaceAll(" ")
        t = repeatPattern.matcher(t).replaceAll("$1$1")
        return t.split("\\s+".toRegex()).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun classify(text: String): Model2Prediction {
        val tflite = interpreter
        if (tflite == null) {
            return Model2Prediction("not_cyberbullying", 0f, false)
        }

        val cleaned = cleanText(text)
        val tokens = cleaned.split(" ").filter { it.isNotBlank() }

        val tokenIds = tokens.map { vocab[it] ?: unkId }
        val truncated = if (tokenIds.size > maxLen) tokenIds.take(maxLen) else tokenIds

        val inputIds = IntArray(maxLen) { padId }
        val startOffset = maxLen - truncated.size
        for (i in truncated.indices) {
            inputIds[startOffset + i] = truncated[i]
        }

        val inputs = Array(1) { inputIds }
        val outputs = Array(1) { FloatArray(labels.size) }

        try {
            synchronized(this) {
                tflite.run(inputs, outputs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val probs = outputs[0]
        val probMap = mutableMapOf<String, Float>()
        for (i in labels.indices) {
            probMap[labels[i]] = probs[i]
        }

        val bestIndex = probs.indices.maxByOrNull { probs[it] } ?: 0
        val bestLabel = labels.getOrElse(bestIndex) { "not_cyberbullying" }
        val confidence = probs[bestIndex]

        val isCyberbullying = bestLabel != "not_cyberbullying" && confidence >= 0.45f

        return Model2Prediction(
            label = bestLabel,
            confidence = confidence,
            isCyberbullying = isCyberbullying,
            probabilities = probMap
        )
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (_: Exception) {}
    }
}
