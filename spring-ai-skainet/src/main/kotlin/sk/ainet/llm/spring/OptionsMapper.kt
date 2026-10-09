package sk.ainet.llm.spring

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.prompt.ChatOptions as SpringChatOptions
import sk.ainet.llm.api.ChatOptions as NeutralChatOptions

/**
 * Spring AI [SpringChatOptions] <-> neutral [NeutralChatOptions].
 *
 * `temperature`, `topK`, `topP`, `maxTokens`, `stopSequences` and `model` map 1:1.
 * `frequencyPenalty` / `presencePenalty` have no counterpart in the SKaiNET
 * runtime today and are dropped with a debug log.
 */
public object OptionsMapper {
    private val log = LoggerFactory.getLogger(OptionsMapper::class.java)

    public fun toNeutral(options: SpringChatOptions?, defaults: NeutralChatOptions): NeutralChatOptions {
        if (options == null) return defaults
        if (options.frequencyPenalty != null || options.presencePenalty != null) {
            log.debug("frequencyPenalty/presencePenalty are not supported by the SKaiNET runtime and are ignored")
        }
        return NeutralChatOptions(
            model = options.model ?: defaults.model,
            temperature = options.temperature?.toFloat() ?: defaults.temperature,
            topK = options.topK ?: defaults.topK,
            topP = options.topP?.toFloat() ?: defaults.topP,
            maxTokens = options.maxTokens ?: defaults.maxTokens,
            stopSequences = options.stopSequences ?: defaults.stopSequences,
            seed = defaults.seed,
        )
    }

    public fun toSpring(options: NeutralChatOptions): SpringChatOptions {
        val b = SpringChatOptions.builder()
        options.model?.let { b.model(it) }
        options.temperature?.let { b.temperature(it.toExactDouble()) }
        options.topK?.let { b.topK(it) }
        options.topP?.let { b.topP(it.toExactDouble()) }
        options.maxTokens?.let { b.maxTokens(it) }
        if (options.stopSequences.isNotEmpty()) b.stopSequences(options.stopSequences)
        return b.build()
    }
}

/** 0.1f -> 0.1, not 0.10000000149: round-trip through the shortest decimal representation. */
private fun Float.toExactDouble(): Double = toString().toDouble()
