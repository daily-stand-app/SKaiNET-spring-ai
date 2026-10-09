package sk.ainet.llm.spring.boot

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import sk.ainet.apps.kllama.chat.ModelMetadataExtraction
import sk.ainet.apps.kllama.chat.ToolCallingSupportResolver
import sk.ainet.apps.llm.OptimizedLLMMode
import sk.ainet.apps.llm.OptimizedLLMRuntime
import sk.ainet.apps.llm.tokenizer.TokenizerFactory
import sk.ainet.backend.api.kernel.KernelDispatch
import sk.ainet.backend.api.kernel.KernelPacks
import sk.ainet.context.DirectCpuExecutionContext
import sk.ainet.io.JvmRandomAccessSource
import sk.ainet.io.gguf.StreamingGGUFReader
import sk.ainet.lang.nn.dsl.decoder.DecoderGgufWeightLoader
import sk.ainet.lang.tensor.data.MemorySegmentTensorDataFactory
import sk.ainet.lang.types.FP32
import sk.ainet.llm.api.ChatOptions
import sk.ainet.llm.api.EmbeddingModel
import sk.ainet.llm.api.StreamingChatModel
import sk.ainet.llm.providers.BertEmbeddingModel
import sk.ainet.llm.providers.SkaiNetChatModel
import sk.ainet.models.llama.LlamaNetworkLoader
import sk.ainet.models.qwen.QwenNetworkLoader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * One-call model loader: GGUF path -> neutral [StreamingChatModel].
 *
 * This is the "one-call loader" the SKaiNET-transformers Spring adapter spec lists
 * as its open blocker; it mirrors the wiring of `skainet-cli` / Daily-StandAPP's
 * `LocalModel` and should move upstream into `llm-providers` once stable.
 */
object SkaiNetModelFactory {
    private val log = LoggerFactory.getLogger(SkaiNetModelFactory::class.java)

    private val LLAMA = setOf("llama")
    private val QWEN = setOf("qwen2", "qwen3")

    fun chatModel(
        modelPath: Path,
        chatTemplate: String = "auto",
        defaults: ChatOptions = ChatOptions.DEFAULTS,
    ): StreamingChatModel {
        require(modelPath.exists()) { "spring.ai.skainet.chat.model-path does not exist: $modelPath" }
        // Kernel packs: without them packed GGUF weights run on the reference kernel.
        KernelDispatch.ensureInstalled()
        KernelPacks.install()

        val metadata = JvmRandomAccessSource.open(modelPath.toString()).use { source ->
            StreamingGGUFReader.open(source).use { reader -> ModelMetadataExtraction.fromGgufFields(reader.fields) }
        }
        val architecture = metadata.architecture
            ?: error("$modelPath: GGUF has no general.architecture")
        val accepted = when (architecture) {
            in LLAMA -> LLAMA
            in QWEN -> QWEN
            else -> error("$modelPath: unsupported architecture '$architecture' (supported: ${LLAMA + QWEN})")
        }
        val tokenizer = JvmRandomAccessSource.open(modelPath.toString()).use { TokenizerFactory.fromGgufSource(it) }

        val started = System.nanoTime()
        val ctx = DirectCpuExecutionContext(tensorDataFactory = MemorySegmentTensorDataFactory())
        val loader = DecoderGgufWeightLoader(
            randomAccessProvider = { JvmRandomAccessSource.open(modelPath.toString()) },
            acceptedArchitectures = accepted,
        )
        val weights = runBlocking { loader.loadToMapStreaming<FP32, Float>(ctx) }
        val module = if (architecture in QWEN) QwenNetworkLoader.fromWeights(weights) else LlamaNetworkLoader.fromWeights(weights)
        val runtime = OptimizedLLMRuntime(
            model = module,
            ctx = ctx,
            mode = OptimizedLLMMode.DIRECT,
            dtype = FP32::class,
            bos = weights.metadata.bosTokenId,
        )

        val explicitFamily = chatTemplate.takeUnless { it.equals("auto", ignoreCase = true) }
        val support = ToolCallingSupportResolver.resolve(metadata, explicitFamily)
            ?: error("$modelPath: no chat template for family=${metadata.family} arch=$architecture (set spring.ai.skainet.chat.chat-template)")
        val turnEnders = if (architecture in QWEN) listOf("<|im_end|>", "<|endoftext|>") else listOf("<|eot_id|>", "<|end_of_text|>")
        val eos = (turnEnders.mapNotNull { tokenizer.encode(it).singleOrNull() } + tokenizer.eosTokenId).toSet()

        log.info(
            "SKaiNET chat model loaded: {} arch={} template={} layers={} ctx={} in {} ms",
            modelPath.name, architecture, support.family, weights.metadata.blockCount, weights.metadata.contextLength,
            (System.nanoTime() - started) / 1_000_000,
        )
        return SkaiNetChatModel(
            runtime = runtime,
            tokenizer = tokenizer,
            chatTemplate = support.createChatTemplate(),
            defaultOptions = defaults,
            eosTokenIds = eos,
            modelId = modelPath.name,
        )
    }

    fun embeddingModel(modelPath: Path?, repoId: String?): EmbeddingModel = when {
        modelPath != null -> BertEmbeddingModel.fromSafeTensors(modelPath)
        repoId != null -> BertEmbeddingModel.fromHuggingFace(repoId)
        else -> error("spring.ai.skainet.embedding: set model-path or repo-id")
    }
}
