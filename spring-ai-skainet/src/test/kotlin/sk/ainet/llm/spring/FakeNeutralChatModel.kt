package sk.ainet.llm.spring

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sk.ainet.llm.api.ChatOptions
import sk.ainet.llm.api.ChatRequest
import sk.ainet.llm.api.ChatResponse
import sk.ainet.llm.api.ChatResponseChunk
import sk.ainet.llm.api.FinishReason
import sk.ainet.llm.api.Generation
import sk.ainet.llm.api.Message
import sk.ainet.llm.api.StreamingChatModel
import sk.ainet.llm.api.ToolCall
import sk.ainet.llm.api.Usage

/** Scripted neutral model: records the last request and answers from a queue. */
class FakeNeutralChatModel(
    override val defaultOptions: ChatOptions = ChatOptions(temperature = 0.1f, maxTokens = 64),
) : StreamingChatModel {
    val requests = mutableListOf<ChatRequest>()
    val answers = ArrayDeque<ChatResponse>()
    var streamTokens: List<String> = listOf("Hel", "lo", " world")

    fun answer(text: String, finish: FinishReason = FinishReason.STOP, toolCalls: List<ToolCall> = emptyList()) {
        answers += ChatResponse(
            generations = listOf(Generation(Message.assistant(text, toolCalls), finish)),
            usage = Usage(promptTokens = 10, completionTokens = 3),
            modelId = "fake",
        )
    }

    override fun call(request: ChatRequest): ChatResponse {
        requests += request
        return answers.removeFirstOrNull() ?: error("no scripted answer left")
    }

    override fun stream(request: ChatRequest): Flow<ChatResponseChunk> = flow {
        requests += request
        streamTokens.forEachIndexed { i, t ->
            val last = i == streamTokens.lastIndex
            emit(ChatResponseChunk(delta = t, finishReason = if (last) FinishReason.STOP else null, usage = if (last) Usage(5, streamTokens.size) else null))
        }
    }
}
