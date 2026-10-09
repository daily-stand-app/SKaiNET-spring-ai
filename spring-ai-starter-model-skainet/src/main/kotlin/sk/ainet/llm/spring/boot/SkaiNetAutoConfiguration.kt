package sk.ainet.llm.spring.boot

import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import sk.ainet.llm.api.ChatOptions
import sk.ainet.llm.spring.SpringSkaiNetChatModel
import sk.ainet.llm.spring.SpringSkaiNetEmbeddingModel
import java.nio.file.Path

@AutoConfiguration
@ConditionalOnClass(SpringSkaiNetChatModel::class)
@EnableConfigurationProperties(SkaiNetProperties::class)
@ConditionalOnProperty(prefix = SkaiNetProperties.PREFIX, name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SkaiNetAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ChatModel::class)
    @ConditionalOnProperty(prefix = SkaiNetProperties.PREFIX, name = ["chat.model-path"])
    fun skaiNetChatModel(props: SkaiNetProperties): SpringSkaiNetChatModel {
        val chat = props.chat
        val o = chat.options
        val defaults = ChatOptions(
            temperature = o.temperature,
            topK = o.topK,
            topP = o.topP,
            maxTokens = o.maxTokens,
            stopSequences = o.stopSequences,
        )
        val path = Path.of(requireNotNull(chat.modelPath))
        val delegate = SkaiNetModelFactory.chatModel(path, chat.chatTemplate, defaults)
        return SpringSkaiNetChatModel(delegate, modelId = path.fileName.toString())
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(EmbeddingModel::class)
    @ConditionalOnProperty(prefix = SkaiNetProperties.PREFIX, name = ["embedding.model-path", "embedding.repo-id"], matchIfMissing = false)
    fun skaiNetEmbeddingModel(props: SkaiNetProperties): SpringSkaiNetEmbeddingModel {
        val e = props.embedding
        return SpringSkaiNetEmbeddingModel(SkaiNetModelFactory.embeddingModel(e.modelPath?.let(Path::of), e.repoId))
    }
}
