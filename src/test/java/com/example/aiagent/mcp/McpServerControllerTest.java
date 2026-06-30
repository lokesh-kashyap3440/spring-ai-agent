package com.example.aiagent.mcp;

import com.example.aiagent.service.DocumentIngestionService;
import com.example.aiagent.tools.CalculatorTool;
import com.example.aiagent.tools.DatabaseTool;
import com.example.aiagent.tools.NewsTool;
import com.example.aiagent.tools.RAGTool;
import com.example.aiagent.tools.WeatherTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpServerControllerTest {

    @Mock
    private WeatherTool weatherTool;

    @Mock
    private NewsTool newsTool;

    @Mock
    private CalculatorTool calculatorTool;

    @Mock
    private DatabaseTool databaseTool;

    @Mock
    private RAGTool ragTool;

    @Mock
    private DocumentIngestionService ingestionService;

    private ObjectMapper objectMapper;
    private McpServerController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new McpServerController(weatherTool, newsTool, calculatorTool,
                databaseTool, ragTool, ingestionService, objectMapper);
    }

    @Test
    void testInitialize() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "1");
        request.put("method", "initialize");
        request.putObject("params");

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        ObjectNode body = (ObjectNode) response.getBody();
        assertNotNull(body);
        assertEquals("2.0", body.get("jsonrpc").asText());
        assertEquals("1", body.get("id").asText());

        JsonNode result = body.get("result");
        assertNotNull(result);
        assertEquals("2024-11-05", result.get("protocolVersion").asText());
        assertEquals("ai-agent-tools", result.get("serverInfo").get("name").asText());
        assertEquals("2.0.0", result.get("serverInfo").get("version").asText());
    }

    @Test
    void testToolsList() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "1");
        request.put("method", "tools/list");
        request.putObject("params");

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        ObjectNode body = (ObjectNode) response.getBody();
        JsonNode result = body.get("result");
        assertNotNull(result);
        JsonNode tools = result.get("tools");
        assertTrue(tools.isArray());
        assertEquals(6, tools.size());

        assertTrue(tools.toString().contains("get_weather"));
        assertTrue(tools.toString().contains("get_news"));
        assertTrue(tools.toString().contains("calculate"));
        assertTrue(tools.toString().contains("query_database"));
        assertTrue(tools.toString().contains("rag_search"));
        assertTrue(tools.toString().contains("upload_document"));
    }

    @Test
    void testToolsCallWeather() {
        when(weatherTool.execute("London")).thenReturn("Sunny, 20°C");

        ObjectNode request = buildToolCall("get_weather",
                objectMapper.createObjectNode().put("city", "London"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Sunny, 20°C"));
    }

    @Test
    void testToolsCallNews() {
        when(newsTool.execute("technology")).thenReturn("Tech news");

        ObjectNode request = buildToolCall("get_news",
                objectMapper.createObjectNode().put("topic", "technology"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Tech news"));
    }

    @Test
    void testToolsCallCalculate() {
        when(calculatorTool.execute("2 + 2")).thenReturn("Result: 4");

        ObjectNode request = buildToolCall("calculate",
                objectMapper.createObjectNode().put("expression", "2 + 2"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Result: 4"));
    }

    @Test
    void testToolsCallDatabase() {
        when(databaseTool.execute("What is Spring Boot?")).thenReturn("Found: Spring Boot info");

        ObjectNode request = buildToolCall("query_database",
                objectMapper.createObjectNode().put("query", "What is Spring Boot?"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Spring Boot"));
    }

    @Test
    void testToolsCallRagSearch() {
        when(ragTool.execute("refund policy")).thenReturn("NO_RESULTS: not found");

        ObjectNode request = buildToolCall("rag_search",
                objectMapper.createObjectNode().put("query", "refund policy"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("NO_RESULTS"));
    }

    @Test
    void testToolsCallUnknown() {
        ObjectNode request = buildToolCall("unknown_tool",
                objectMapper.createObjectNode());

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Unknown tool: unknown_tool"));
    }

    @Test
    void testMethodNotFound() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "1");
        request.put("method", "resources/list");
        request.putObject("params");

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        ObjectNode body = (ObjectNode) response.getBody();
        JsonNode error = body.get("error");
        assertNotNull(error);
        assertEquals(-32601, error.get("code").asInt());
        assertTrue(error.get("message").asText().contains("Method not found"));
    }

    @Test
    void testNullIdReturnsNoContent() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void testUploadDocument() throws Exception {
        when(ingestionService.ingest(any())).thenReturn(
                new com.example.aiagent.model.DocumentInfo("d1", "test.pdf", "application/pdf", 100, 3));

        String base64 = java.util.Base64.getEncoder().encodeToString("test content".getBytes());
        ObjectNode args = objectMapper.createObjectNode();
        args.put("filename", "test.pdf");
        args.put("content", base64);
        args.put("contentType", "application/pdf");

        ObjectNode request = buildToolCall("upload_document", args);

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Uploaded"));
        assertTrue(text.contains("test.pdf"));
    }

    @Test
    void testUploadDocumentInvalidBase64() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("filename", "test.pdf");
        args.put("content", "not-valid-base64!!!");
        args.put("contentType", "application/pdf");

        ObjectNode request = buildToolCall("upload_document", args);

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Invalid base64"));
    }

    @Test
    void testDetectContentType() {
        assertEquals("application/pdf",
                invokeDetectContentType("document.pdf"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                invokeDetectContentType("report.docx"));
        assertEquals("text/plain",
                invokeDetectContentType("notes.txt"));
        assertEquals("text/markdown",
                invokeDetectContentType("readme.md"));
        assertEquals("text/html",
                invokeDetectContentType("index.html"));
        assertEquals("text/html",
                invokeDetectContentType("page.htm"));
        assertEquals("application/octet-stream",
                invokeDetectContentType("unknown.xyz"));
    }

    private String invokeDetectContentType(String filename) {
        try {
            var method = McpServerController.class.getDeclaredMethod("detectContentType", String.class);
            method.setAccessible(true);
            return (String) method.invoke(controller, filename);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testSseConnection() throws Exception {
        ResponseEntity<Object> response = controller.handleStreamableHttp(
                objectMapper.createObjectNode());

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void testHandleMessageNoEmitter() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", "1");
        request.put("method", "initialize");

        ResponseEntity<Void> response = controller.handleMessage(request, "nonexistent");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void testToolsCallWithMissingArguments() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "1");
        request.put("method", "tools/call");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "get_weather");
        params.putObject("arguments");
        request.set("params", params);

        ResponseEntity<Object> response = controller.handleStreamableHttp(request);
        assertEquals(200, response.getStatusCode().value());
    }

    private ObjectNode buildToolCall(String name, ObjectNode arguments) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "1");
        request.put("method", "tools/call");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments);
        request.set("params", params);
        return request;
    }

    private String extractTextResponse(ResponseEntity<Object> response) {
        ObjectNode body = (ObjectNode) response.getBody();
        assertNotNull(body);
        return body.get("result").get("content").get(0).get("text").asText();
    }
}
