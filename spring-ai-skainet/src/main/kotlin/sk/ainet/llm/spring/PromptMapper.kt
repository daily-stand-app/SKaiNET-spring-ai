package sk.ainet.llm.spring

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message as SpringMessage
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse
import org.springframework.ai.chat.model.Generation as SpringGeneration
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import sk.ainet.llm.api.ChatRequest
import sk.ainet.llm.api.ChatResponse as NeutralChatResponse
import sk.ainet.llm.api.ChatResponseChunk
import sk.ainet.llm.api.FinishReason
import sk.ainet.llm.api.Message as NeutralMessage
import sk.ainet.llm.api.Role
import sk.ainet.llm.api.ToolCall as NeutralToolCall
import sk.ainet.llm.api.ToolDefinition
import sk.ainet.llm.api.Usage as NeutralUsage
import sk.ainet.llm.api.ChatOptions as NeutralChatOptions

/** Spring AI [Prompt] / [SpringChatResponse] <-> neutral [ChatRequest] / [NeutralChatResponse]. */
public object PromptMapper {

    public fun toRequest(prompt: Prompt, defaults: NeutralChatOptions): ChatRequest {
        val messages = prompt.instructions.flatMap(::toNeutral)
        val options = OptionsMapper.toNeutral(prompt.options, defaults)
        val tools = (prompt.options as? ToolCallingChatOptions)?.toolCallbacks.orEmpty().map { cb ->
            val def = cb.toolDefinition
            ToolDefinition(name = def.name(), description = def.description(), parametersJsonSchema = def.inputSchema())
        }
        return ChatRequest(messages = messages, options = options, tools = tools)
    }

    public fun toNeutral(message: SpringMessage): List<NeutralMessage> = when (message.messageType) {
        MessageType.SYSTEM -> listOf(NeutralMessage.system(message.text.orEmpty()))
        MessageType.USER -> listOf(NeutralMessage.user(message.text.orEmpty()))
        MessageType.ASSISTANT -> {
            val assistant = message as AssistantMessage
            val calls = assistant.toolCalls.map { NeutralToolCall(id = it.id(), name = it.name(), argumentsJson = it.arguments()) }
            listOf(NeutralMessage.assistant(assistant.text.orEmpty(), calls))
        }
        MessageType.TOOL -> (message as ToolResponseMessage).responses.map { r ->
            NeutralMessage.tool(content = r.responseData(), toolCallId = r.id(), name = r.name())
        }
    }

    public fun toSpring(response: NeutralChatResponse): SpringChatResponse {
        val generations = response.generations.map { g ->
            val assistant = AssistantMessage.builder()
                .content(g.message.content)
                .toolCalls(g.message.toolCalls.map { toSpringToolCall(it) })
                .build()
            SpringGeneration(assistant, ChatGenerationMetadata.builder().finishReason(finishReason(g.finishReason)).build())
        }
        return SpringChatResponse(generations, metadata(response.usage, response.modelId))
    }

    /** One streaming delta becomes one [SpringChatResponse] with a single generation. */
    public fun toSpring(chunk: ChatResponseChunk, modelId: String?): SpringChatResponse {
        val assistant = AssistantMessage.builder()
            .content(chunk.delta)
            .toolCalls(chunk.toolCallDelta.map { toSpringToolCall(it) })
            .build()
        val genMeta = chunk.finishReason?.let { ChatGenerationMetadata.builder().finishReason(finishReason(it)).build() }
            ?: ChatGenerationMetadata.NULL
        return SpringChatResponse(listOf(SpringGeneration(assistant, genMeta)), metadata(chunk.usage, modelId))
    }

    private fun toSpringToolCall(c: NeutralToolCall) = AssistantMessage.ToolCall(c.id, "function", c.name, c.argumentsJson)

    private fun metadata(usage: NeutralUsage?, modelId: String?): ChatResponseMetadata {
        val b = ChatResponseMetadata.builder()
        modelId?.let { b.model(it) }
        usage?.let { b.usage(DefaultUsage(it.promptTokens, it.completionTokens)) }
        return b.build()
    }

    /** OpenAI-style finish-reason strings, which Spring AI's tool loop and tests expect. */
    public fun finishReason(reason: FinishReason): String = when (reason) {
        FinishReason.STOP -> "STOP"
        FinishReason.LENGTH -> "LENGTH"
        FinishReason.TOOL_CALL -> "TOOL_CALLS"
        FinishReason.ERROR -> "ERROR"
    }
}
