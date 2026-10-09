package sk.ainet.llm.spring

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import org.springframework.ai.chat.model.ChatModel as SpringChatModel
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse
import org.springframework.ai.chat.prompt.ChatOptions as SpringChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.DefaultToolCallingManager
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker
import org.springframework.ai.model.tool.ToolExecutionResult
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import sk.ainet.llm.api.ChatModel as NeutralChatModel
import sk.ainet.llm.api.StreamingChatModel as NeutralStreamingChatModel

/**
 * Spring AI [SpringChatModel] (which includes `StreamingChatModel` in Spring AI 2.x)
 * backed by any SKaiNET-transformers neutral [NeutralChatModel].
 *
 * Tool calling follows the Spring AI model-implementation contract: when the
 * [ToolExecutionEligibilityChecker] accepts a response (default: it carries tool
 * calls), the [ToolCallingManager] runs the tools and the model is called again
 * with the extended conversation; `returnDirect` tools short-circuit.
 *
 * The delegate is typically **not** thread-safe (it owns the KV cache); this
 * class serialises calls on it.
 */
public class SpringSkaiNetChatModel @JvmOverloads constructor(
    private val delegate: NeutralChatModel,
    private val toolCallingManager: ToolCallingManager = DefaultToolCallingManager.builder().build(),
    private val modelId: String? = null,
    private val eligibility: ToolExecutionEligibilityChecker = ToolExecutionEligibilityChecker { it.hasToolCalls() },
) : SpringChatModel, AutoCloseable {

    private val lock = Any()

    override fun call(prompt: Prompt): SpringChatResponse {
        val response = synchronized(lock) {
            PromptMapper.toSpring(delegate.call(PromptMapper.toRequest(prompt, delegate.defaultOptions)))
        }
        if (eligibility.isToolCallResponse(response)) {
            val result = toolCallingManager.executeToolCalls(prompt, response)
            return if (result.returnDirect()) {
                SpringChatResponse(ToolExecutionResult.buildGenerations(result), response.metadata)
            } else {
                call(Prompt(result.conversationHistory(), prompt.options))
            }
        }
        return response
    }

    override fun stream(prompt: Prompt): Flux<SpringChatResponse> {
        val streaming = delegate as? NeutralStreamingChatModel
            ?: return Flux.defer { Mono.fromCallable { call(prompt) } }
        val request = PromptMapper.toRequest(prompt, delegate.defaultOptions)
        // One Flux per subscription; the KV-cache-owning delegate is serialised by `lock`
        // for blocking calls, streaming callers are expected to subscribe one at a time.
        return Flux.defer {
            streaming.stream(request).map { chunk -> PromptMapper.toSpring(chunk, modelId) }.asFlux()
        }
    }

    override fun getDefaultOptions(): SpringChatOptions = OptionsMapper.toSpring(delegate.defaultOptions)

    override fun close(): Unit = delegate.close()
}
