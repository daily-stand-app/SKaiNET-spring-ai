package sk.ainet.llm.spring

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel as SpringEmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest as SpringEmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse as SpringEmbeddingResponse
import sk.ainet.llm.api.EmbeddingModel as NeutralEmbeddingModel
import sk.ainet.llm.api.EmbeddingRequest as NeutralEmbeddingRequest

/** Spring AI [SpringEmbeddingModel] backed by a SKaiNET-transformers neutral [NeutralEmbeddingModel]. */
public class SpringSkaiNetEmbeddingModel(
    private val delegate: NeutralEmbeddingModel,
) : SpringEmbeddingModel, AutoCloseable {

    override fun call(request: SpringEmbeddingRequest): SpringEmbeddingResponse {
        val response = delegate.call(NeutralEmbeddingRequest(request.instructions))
        val embeddings = response.embeddings.sortedBy { it.index }.map { Embedding(it.vector, it.index) }
        return SpringEmbeddingResponse(embeddings)
    }

    override fun embed(document: Document): FloatArray = delegate.embedDocument(document.text.orEmpty())

    override fun dimensions(): Int = delegate.dimensions

    override fun close(): Unit = delegate.close()
}
