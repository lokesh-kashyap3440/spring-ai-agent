package com.example.aiagent.mcp;

import com.example.aiagent.security.JwtUtil;
import com.example.aiagent.service.DocumentIngestionService;
import com.example.aiagent.tools.Tool;
import com.example.aiagent.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpServerControllerTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private DocumentIngestionService ingestionService;

    private ObjectMapper objectMapper;
    private McpServerController controller;
    private MockHttpServletRequest mockRequest;

    private Tool weatherTool;
    private Tool newsTool;
    private Tool calculatorTool;
    private Tool databaseTool;
    private Tool ragTool;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockRequest = new MockHttpServletRequest();

        weatherTool = mock(Tool.class);
        when(weatherTool.getName()).thenReturn("weather");
        when(weatherTool.getDescription()).thenReturn("Get current weather for a city");
        when(weatherTool.getParameterSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of("city", Map.of("type", "string")),
                "required", java.util.List.of("city")
        ));

        newsTool = mock(Tool.class);
        when(newsTool.getName()).thenReturn("news");
        when(newsTool.getDescription()).thenReturn("Get latest news headlines for a topic");
        when(newsTool.getParameterSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of("topic", Map.of("type", "string")),
                "required", java.util.List.of("topic")
        ));

        calculatorTool = mock(Tool.class);
        when(calculatorTool.getName()).thenReturn("calculator");
        when(calculatorTool.getDescription()).thenReturn("Evaluate mathematical expressions");
        when(calculatorTool.getParameterSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of("expression", Map.of("type", "string")),
                "required", java.util.List.of("expression")
        ));

        databaseTool = mock(Tool.class);
        when(databaseTool.getName()).thenReturn("knowledge_base");
        when(databaseTool.getDescription()).thenReturn("Query the knowledge base for information");
        when(databaseTool.getParameterSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", java.util.List.of("query")
        ));

        ragTool = mock(Tool.class);
        when(ragTool.getName()).thenReturn("rag_search");
        when(ragTool.getDescription()).thenReturn("Search uploaded documents for relevant information");
        when(ragTool.getParameterSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", java.util.List.of("query")
        ));

        java.util.Map<String, Tool> toolMap = new java.util.HashMap<>();
        toolMap.put("weather", weatherTool);
        toolMap.put("news", newsTool);
        toolMap.put("calculator", calculatorTool);
        toolMap.put("knowledge_base", databaseTool);
        toolMap.put("rag_search", ragTool);
        when(toolRegistry.getAllTools()).thenReturn(toolMap);
        when(toolRegistry.getTool("weather")).thenReturn(weatherTool);
        when(toolRegistry.getTool("news")).thenReturn(newsTool);
        when(toolRegistry.getTool("calculator")).thenReturn(calculatorTool);
        when(toolRegistry.getTool("knowledge_base")).thenReturn(databaseTool);
        when(toolRegistry.getTool("rag_search")).thenReturn(ragTool);

        controller = new McpServerController(toolRegistry, ingestionService,
                objectMapper, jwtUtil);
    }

    @Test
    void testInitialize() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "1");
        request.put("method", "initialize");
        request.putObject("params");

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

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

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

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
        assertTrue(tools.toString().contains("query_knowledge_base"));
        assertTrue(tools.toString().contains("rag_search"));
        assertTrue(tools.toString().contains("upload_document"));
    }

    @Test
    void testToolsCallWeather() {
        when(weatherTool.execute("London")).thenReturn("Sunny, 20°C");

        ObjectNode request = buildToolCall("get_weather",
                objectMapper.createObjectNode().put("city", "London"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Sunny, 20°C"));
    }

    @Test
    void testToolsCallNews() {
        when(newsTool.execute("technology")).thenReturn("Tech news");

        ObjectNode request = buildToolCall("get_news",
                objectMapper.createObjectNode().put("topic", "technology"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Tech news"));
    }

    @Test
    void testToolsCallCalculate() {
        when(calculatorTool.execute("2 + 2")).thenReturn("Result: 4");

        ObjectNode request = buildToolCall("calculate",
                objectMapper.createObjectNode().put("expression", "2 + 2"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Result: 4"));
    }

    @Test
    void testToolsCallKnowledgeBase() {
        when(databaseTool.execute("What is Spring Boot?")).thenReturn("Found: Spring Boot info");

        ObjectNode request = buildToolCall("query_knowledge_base",
                objectMapper.createObjectNode().put("query", "What is Spring Boot?"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("Spring Boot"));
    }

    @Test
    void testToolsCallRagSearch() {
        when(ragTool.execute("refund policy")).thenReturn("NO_RESULTS: not found");

        ObjectNode request = buildToolCall("rag_search",
                objectMapper.createObjectNode().put("query", "refund policy"));

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

        assertEquals(200, response.getStatusCode().value());
        String text = extractTextResponse(response);
        assertTrue(text.contains("NO_RESULTS"));
    }

    @Test
    void testToolsCallUnknown() {
        ObjectNode request = buildToolCall("unknown_tool",
                objectMapper.createObjectNode());

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

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

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

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

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void testUploadDocument() throws Exception {
        when(ingestionService.ingest(any(), anyString())).thenReturn(
                new com.example.aiagent.model.DocumentInfo("d1", "test.pdf", "application/pdf", 100, 3, null));

        String base64 = java.util.Base64.getEncoder().encodeToString("test content".getBytes());
        ObjectNode args = objectMapper.createObjectNode();
        args.put("filename", "test.pdf");
        args.put("content", base64);
        args.put("contentType", "application/pdf");

        ObjectNode request = buildToolCall("upload_document", args);

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

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

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);

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
                objectMapper.createObjectNode(), Optional.empty(), mockRequest);

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void testHandleMessageNoEmitter() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", "1");
        request.put("method", "initialize");

        ResponseEntity<Void> response = controller.handleMessage(request,
                "nonexistent", Optional.empty(), mockRequest);

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

        ResponseEntity<Object> response = controller.handleStreamableHttp(request,
                Optional.empty(), mockRequest);
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
