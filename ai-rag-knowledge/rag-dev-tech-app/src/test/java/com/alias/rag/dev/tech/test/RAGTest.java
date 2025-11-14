package com.alias.rag.dev.tech.test;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {

    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void upload() {
        TikaDocumentReader reader = new TikaDocumentReader("ai-rag-knowledge/data/file.text");

        List<Document> documents = reader.get();
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        documents.forEach(doc -> doc.getMetadata().put("knowledge", "ai-rag-knowledge"));
        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "ai-rag-knowledge"));

        pgVectorStore.accept(documentSplitterList);

        log.info("上传完成");
    }

    @Test
    public void chat() {
        String message = "王大瓜，哪年出生";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        SearchRequest request = SearchRequest.builder().query(message).topK(5).filterExpression("knowledge == 'ai-rag-knowledge'").build();

        List<Document> documents = pgVectorStore.similaritySearch(request);
        String documentsCollectors = documents.stream().map(Document::getFormattedContent).collect(Collectors.joining());

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        log.info("message: {}", messages);

    }

    @Test
    public void test_testAddDocuments() {
        // 模拟创建几个文档
        List<Document> docs = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Document doc = new Document("Content of doc " + i);
            doc.getMetadata().put("id", "test-doc-" + i);
            doc.getMetadata().put("repo", "test-repo");
            docs.add(doc);
        }

        // 调用 add
        pgVectorStore.add(docs);

        System.out.println("Added documents: " + docs.size());
    }

    @Test
    public void test_testQueryDocuments() {
        // 构建过滤表达式：repo == "test-repo"
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter = builder.eq("repo", "test-repo").build();

        SearchRequest request = SearchRequest.builder().query("search all").topK(5).filterExpression(filter).build();

        List<Document> documents = pgVectorStore.similaritySearch(request);
        System.out.println("Found documents: " + documents.size());
        for (Document doc : documents) {
            System.out.println("----------------------");
            System.out.println("Doc id: " + doc.getMetadata().get("id"));
            System.out.println("Content: " + doc.getFormattedContent());
        }
    }

    @Test
    public void test_testDeleteDocuments() {
        // FIXME: 批量删除不起作用
        // 模拟需要删除的 docId
        List<String> idsToDelete = List.of("test-doc-1", "test-doc-2", "test-doc-3");

        for (String id : idsToDelete) {
            SearchRequest request = SearchRequest.builder()
                    .query("search all")
                    .topK(1)
                    .filterExpression(new FilterExpressionBuilder().eq("id", id).build())
                    .build();
            List<Document> documents = pgVectorStore.similaritySearch(request);

            List<String> docIds = documents.stream().map(Document::getId).collect(Collectors.toList());
            log.info("docIds: {}", docIds);

            pgVectorStore.delete(docIds);
        }

//        // 构建 filter
//        FilterExpressionBuilder builder = new FilterExpressionBuilder();
//        Filter.Expression filter = builder.in("id", String.join(",", idsToDelete)).build();
//
//        pgVectorStore.delete(filter);
//
//        System.out.println("Deleted documents with filter: " + filter);
    }

}
