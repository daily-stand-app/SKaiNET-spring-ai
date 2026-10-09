package sk.ainet.llm.spring.boot

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `spring.ai.skainet.*` — see README for the YAML shape.
 */
@ConfigurationProperties(prefix = SkaiNetProperties.PREFIX)
class SkaiNetProperties {
    var enabled: Boolean = true
    var chat: Chat = Chat()
    var embedding: Embedding = Embedding()

    class Chat {
        /** Path to a GGUF checkpoint (Llama 3.x or Qwen 2.5/3). Required to create the ChatModel bean. */
        var modelPath: String? = null
        /** `auto` (detect from GGUF metadata) or an explicit family: `llama3`, `qwen`, `gemma`, `chatml`, ... */
        var chatTemplate: String = "auto"
        var options: Options = Options()
    }

    class Options {
        var temperature: Float = 0.7f
        var maxTokens: Int = 512
        /** Accepted and forwarded; the SKaiNET runtime currently samples with temperature only. */
        var topK: Int? = null
        var topP: Float? = null
        var stopSequences: List<String> = emptyList()
    }

    class Embedding {
        /** Local sentence-transformers / BERT directory (safetensors + tokenizer + config.json). */
        var modelPath: String? = null
        /** Or a Hugging Face repo id (downloaded and cached by SKaiNET-transformers). */
        var repoId: String? = null
    }

    companion object {
        const val PREFIX = "spring.ai.skainet"
    }
}
