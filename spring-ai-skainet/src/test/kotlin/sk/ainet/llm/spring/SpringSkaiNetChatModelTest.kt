package sk.ainet.llm.spring

import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.tool.function.FunctionToolCallback
import sk.ainet.llm.api.FinishReason
import sk.ainet.llm.api.Role
import sk.ainet.llm.api.ToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpringSkaiNetChatModelTest {

    @Test
    fun `call maps messages, options and usage both ways`() {
        val fake = FakeNeutralChatModel()
        fake.answer("Hi there")
        val model = SpringSkaiNetChatModel(fake)

        val options = ChatOptions.builder().temperature(0.5).maxTokens(32).topK(40).topP(0.9).build()
        val response = model.call(Prompt(listOf(SystemMessage("be brief"), UserMessage("hello")), options))

        val req = fake.requests.single()
        assertEquals(listOf(Role.SYSTEM, Role.USER), req.messages.map { it.role })
        assertEquals("hello", req.messages[1].content)
        assertEquals(0.5f, req.options?.temperature)
        assertEquals(32, req.options?.maxTokens)
        assertEquals(40, req.options?.topK)

        assertEquals("Hi there", response.result!!.output.text)
        assertEquals("STOP", response.result!!.metadata.finishReason)
        assertEquals(10, response.metadata.usage.promptTokens)
        assertEquals(3, response.metadata.usage.completionTokens)
        assertEquals("fake", response.metadata.model)
    }

    @Test
    fun `default options come from the delegate`() {
        val model = SpringSkaiNetChatModel(FakeNeutralChatModel())
        assertEquals(0.1, model.defaultOptions.temperature)
        assertEquals(64, model.defaultOptions.maxTokens)
    }

    @Test
    fun `stream emits one ChatResponse per chunk and a final finish reason`() {
        val model = SpringSkaiNetChatModel(FakeNeutralChatModel())
        val chunks = model.stream(Prompt("hello")).collectList().block()!!

        assertEquals(3, chunks.size)
        assertEquals("Hello world", chunks.joinToString("") { it.result!!.output.text.orEmpty() })
        assertEquals("STOP", chunks.last().result!!.metadata.finishReason)
        assertEquals(3, chunks.last().metadata.usage.completionTokens)
    }

    @Test
    fun `tool calls are executed through the ToolCallingManager and fed back`() {
        val fake = FakeNeutralChatModel()
        fake.answer("", FinishReason.TOOL_CALL, listOf(ToolCall(id = "c1", name = "weather", argumentsJson = """{"city":"Darmstadt"}""")))
        fake.answer("It is sunny in Darmstadt")
        val model = SpringSkaiNetChatModel(fake)

        // Explicit Function: a bare Kotlin lambda resolves to the Consumer overload and the tool returns null.
        val weather = FunctionToolCallback.builder("weather", java.util.function.Function<WeatherRequest, String> { req -> "sunny in ${req.city}" })
            .description("Weather by city")
            .inputType(WeatherRequest::class.java)
            .build()
        val options = ToolCallingChatOptions.builder().toolCallbacks(weather).build()

        val response = model.call(Prompt("How is the weather in Darmstadt?", options))

        assertEquals("It is sunny in Darmstadt", response.result!!.output.text)
        // first request carried the tool definition, second one the tool result
        assertEquals("weather", fake.requests[0].tools.single().name)
        val second = fake.requests[1].messages
        assertTrue(second.any { it.role == Role.TOOL && it.content.contains("sunny") }, "tool result missing in: $second")
    }

    /** Plain bean: the ToolCallingManager deserialises arguments with Jackson, no Kotlin module on the classpath. */
    class WeatherRequest {
        var city: String = ""
    }
}
