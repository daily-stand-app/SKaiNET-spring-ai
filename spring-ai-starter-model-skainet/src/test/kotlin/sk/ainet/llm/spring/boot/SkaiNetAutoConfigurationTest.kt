package sk.ainet.llm.spring.boot

import org.springframework.ai.chat.model.ChatModel
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkaiNetAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SkaiNetAutoConfiguration::class.java))

    @Test
    fun `no model path - no ChatModel bean, context still starts`() {
        runner.run { ctx ->
            assertFalse(ctx.containsBean("skaiNetChatModel"))
            assertTrue(ctx.getBeansOfType(SkaiNetAutoConfiguration::class.java).isNotEmpty())
        }
    }

    @Test
    fun `disabled - auto-configuration is skipped`() {
        runner.withPropertyValues("spring.ai.skainet.enabled=false").run { ctx ->
            assertTrue(ctx.getBeansOfType(SkaiNetAutoConfiguration::class.java).isEmpty())
        }
    }

    @Test
    fun `missing model file fails fast with a clear message`() {
        runner.withPropertyValues("spring.ai.skainet.chat.model-path=/nonexistent/model.gguf").run { ctx ->
            assertTrue(ctx.startupFailure != null)
            val msg = generateSequence(ctx.startupFailure!!) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
            assertTrue("model-path does not exist" in msg, msg)
        }
    }

    /** Real-model smoke test; runs only when SKAINET_TEST_GGUF points at a Llama/Qwen GGUF. */
    @Test
    fun `real GGUF - ChatModel answers`() {
        val gguf = System.getenv("SKAINET_TEST_GGUF") ?: return
        runner.withPropertyValues(
            "spring.ai.skainet.chat.model-path=$gguf",
            "spring.ai.skainet.chat.options.max-tokens=16",
        ).run { ctx ->
            val model = ctx.getBean(ChatModel::class.java)
            val answer = model.call("Say hello in one word.")
            assertTrue(!answer.isNullOrBlank(), "empty answer")
            println("[real GGUF] $answer")
        }
    }
}
