# SKaiNET-spring-ai

Spring AI adapter and Spring Boot starter for [SKaiNET-transformers](https://github.com/SKaiNET-developers/SKaiNET-transformers):
run Llama 3.x / Qwen 2.5 / Qwen 3 GGUF models **in-process on the JVM** behind the standard
Spring AI `ChatModel` / `StreamingChatModel` / `EmbeddingModel` contracts and `ChatClient`.

SKaiNET and SKaiNET-transformers stay free of Spring and Reactor. Their `llm-api` module is a
framework-neutral SPI (`sk.ainet.llm.api.ChatModel`, ...) shaped like Spring AI's; this repository
is the only place where the two meet.

| Module | Artifact | What it is |
|---|---|---|
| `spring-ai-skainet` | `sk.ainet.spring:spring-ai-skainet` | `SpringSkaiNetChatModel`, `SpringSkaiNetEmbeddingModel`, prompt/options mappers, Flow→Flux streaming bridge, Spring AI tool-calling loop |
| `spring-ai-starter-model-skainet` | `sk.ainet.spring:spring-ai-starter-model-skainet` | `@AutoConfiguration` + `spring.ai.skainet.*` properties; loads a GGUF into an `OptimizedLLMRuntime` and exposes it as a `ChatModel` bean |
| `samples/chat-app` | — | 30-line Boot app: `POST /chat`, `GET /chat/stream` (SSE) via `ChatClient` |

## Versions

| | |
|---|---|
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| SKaiNET | 0.57.0 |
| SKaiNET-transformers | 0.57.1 |
| JDK | 25 (SKaiNET jars are Java 21 bytecode; the native FFM kernel pack needs 22+) |

## Use it

```kotlin
dependencies {
    implementation("sk.ainet.spring:spring-ai-starter-model-skainet:0.1.0-SNAPSHOT")
}
```

```yaml
spring.ai.skainet:
  chat:
    model-path: /models/Qwen3-0.6B-Q8_0.gguf   # Llama 3.x or Qwen 2.5/3 GGUF
    chat-template: auto                        # or llama3 | qwen | gemma | chatml
    options:
      temperature: 0.2
      max-tokens: 256
  embedding:
    repo-id: MongoDB/mdbr-leaf-mt              # or model-path: /models/bge-small-en
```

```kotlin
@RestController
class ChatController(builder: ChatClient.Builder) {
    private val chat = builder.build()
    @PostMapping("/chat") fun chat(@RequestBody q: String) = chat.prompt().user(q).call().content()
}
```

JVM flags for the runtime: `--add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED`
(the sample's `bootRun` sets them).

Tool calling: pass `ToolCallbacks` / `@Tool` beans as usual; the adapter maps them to the neutral
`ToolDefinition`s, SKaiNET-transformers renders them with the model family's native template
(Llama 3 JSON, Qwen Hermes `<tool_call>`, Gemma `functionCall`, ...), and the Spring AI
`ToolCallingManager` runs the tools and feeds results back.

## Build and test

```bash
./gradlew build                                   # adapter unit tests + autoconfig tests, no model needed
SKAINET_TEST_GGUF=~/.cache/standapp/models/Llama-3.2-1B-Instruct-Q8_0.gguf ./gradlew :spring-ai-starter-model-skainet:test
SKAINET_MODEL_PATH=/models/Qwen3-0.6B-Q8_0.gguf ./gradlew :samples:chat-app:bootRun
curl -s -X POST localhost:8080/chat -H 'Content-Type: text/plain' -d 'Who are you?'
curl -N 'localhost:8080/chat/stream?q=hello'
```

## Status

- [x] `spring-ai-skainet`: call, stream, options, usage, tool-calling loop — unit-tested against a scripted neutral model
- [x] `spring-ai-starter-model-skainet`: auto-configuration, properties, fail-fast on a missing model, one-call GGUF loader
- [x] Real-GGUF smoke test (`SKAINET_TEST_GGUF`): Llama-3.2-1B-Instruct Q8_0 loads in ~2 s and answers through `ChatModel.call` (local run, 2026-10-09)
- [ ] CI on a real GGUF (needs a cached model on the runner)
- [ ] `EmbeddingModel` sample + cosine-similarity test
- [ ] Move `SkaiNetModelFactory.chatModel()` upstream into `llm-providers` (`ModelLoader.fromGguf`), the blocker named in the adapter spec
- [ ] Publish to Maven Central under `sk.ainet.spring`

Spec: `docs/specs/spring-ai-adapter.md` in SKaiNET-transformers.
