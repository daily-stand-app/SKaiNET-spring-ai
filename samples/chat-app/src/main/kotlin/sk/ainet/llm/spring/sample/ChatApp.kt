package sk.ainet.llm.spring.sample

import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@SpringBootApplication
class ChatApp

fun main(args: Array<String>) {
    runApplication<ChatApp>(*args)
}

@RestController
class ChatController(builder: ChatClient.Builder) {
    private val chat = builder.build()

    @PostMapping("/chat", consumes = [MediaType.TEXT_PLAIN_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun chat(@RequestBody question: String): String = chat.prompt().user(question).call().content().orEmpty()

    @GetMapping("/chat/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@RequestParam q: String): Flux<String> = chat.prompt().user(q).stream().content()
}
