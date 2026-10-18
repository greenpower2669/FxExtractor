package fr.fxextractor

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer

/**
 * YAMNet bridge preserving the original extractor while sharing the current LiteRT CPU
 * runtime with Stable Audio 3. All calls are confined to MainActivity.worker.
 * The actual official YAMNet checkpoint is already verified in GitHub Actions.
 */
class LiteRtYamnet(context: Context) : AutoCloseable {
    private val env = Environment.create()
    private val model: CompiledModel
    private val inputBuffers: List<TensorBuffer>
    private val outputBuffers: List<TensorBuffer>

    init {
        model = CompiledModel.create(context.assets, "yamnet.tflite",
            CompiledModel.Options(Accelerator.CPU), env)
        try {
            inputBuffers = model.createInputBuffers()
            outputBuffers = model.createOutputBuffers()
            check(inputBuffers.size == 1 && outputBuffers.size >= 1) {
                "Contrat YAMNet modifié"
            }
        } catch (error: Throwable) {
            model.close()
            env.close()
            throw error
        }
    }

    fun classify(pcm16kMono: FloatArray): FloatArray {
        require(pcm16kMono.size == 15600) { "YAMNet exige 15600 échantillons 16 kHz" }
        inputBuffers[0].writeFloat(pcm16kMono)
        model.run(inputBuffers, outputBuffers)
        val scores = outputBuffers[0].readFloat()
        check(scores.size == 521) { "YAMNet a rendu ${scores.size} classes et non 521" }
        return scores
    }

    override fun close() {
        (inputBuffers + outputBuffers).forEach { it.close() }
        model.close()
        env.close()
    }
}
