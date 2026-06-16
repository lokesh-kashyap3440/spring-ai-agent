package com.example.aiagent.mcp;

import com.example.aiagent.service.DocumentIngestionService;
import com.example.aiagent.tools.CalculatorTool;
import com.example.aiagent.tools.DatabaseTool;
import com.example.aiagent.tools.NewsTool;
import com.example.aiagent.tools.RAGTool;
import com.example.aiagent.tools.WeatherTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/mcp")
@CrossOrigin(origins = "*")
public class McpServerController {

    private static final Logger log = LoggerFactory.getLogger(McpServerController.class);

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final WeatherTool weatherTool;
    private final NewsTool newsTool;
    private final CalculatorTool calculatorTool;
    private final DatabaseTool databaseTool;
    private final RAGTool ragTool;
    private final DocumentIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public McpServerController(WeatherTool weatherTool, NewsTool newsTool,
                               CalculatorTool calculatorTool, DatabaseTool databaseTool,
                               RAGTool ragTool, DocumentIngestionService ingestionService,
                               ObjectMapper objectMapper) {
        this.weatherTool = weatherTool;
        this.newsTool = newsTool;
        this.calculatorTool = calculatorTool;
        this.databaseTool = databaseTool;
        this.ragTool = ragTool;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));

        try {
            emitter.send(SseEmitter.event()
                .name("endpoint")
                .data("/mcp/message?sessionId=" + sessionId));
        } catch (IOException e) {
            log.error("Failed to send endpoint event", e);
            emitters.remove(sessionId);
        }

        return emitter;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> handleStreamableHttp(@RequestBody ObjectNode request) {
        String method = request.has("method") ? request.get("method").asText() : null;
        JsonNode params = request.has("params") ? request.get("params") : null;
        String id = request.has("id") ? request.get("id").asText() : null;

        log.info("MCP streamable HTTP: method={}, id={}", method, id);

        if (id == null) {
            return ResponseEntity.noContent().build();
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        try {
            switch (method) {
                case "initialize" -> handleInitialize(response);
                case "tools/list" -> handleToolsList(response);
                case "tools/call" -> handleToolsCall(params, response);
                default -> {
                    response.putObject("error")
                        .put("code", -32601)
                        .put("message", "Method not found: " + method);
                }
            }
        } catch (Exception e) {
            log.error("MCP error: {}", e.getMessage());
            response.putObject("error")
                .put("code", -32603)
                .put("message", "Internal error: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleMessage(@RequestBody ObjectNode request,
                                               @RequestParam String sessionId) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            return ResponseEntity.notFound().build();
        }

        String method = request.has("method") ? request.get("method").asText() : null;
        JsonNode params = request.has("params") ? request.get("params") : null;
        String id = request.has("id") ? request.get("id").asText() : null;

        log.info("MCP request: method={}, id={}", method, id);

        if (id == null) {
            return ResponseEntity.accepted().build();
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        try {
            switch (method) {
                case "initialize" -> handleInitialize(response);
                case "tools/list" -> handleToolsList(response);
                case "tools/call" -> handleToolsCall(params, response);
                default -> {
                    response.putObject("error")
                        .put("code", -32601)
                        .put("message", "Method not found: " + method);
                }
            }
        } catch (Exception e) {
            log.error("MCP error: {}", e.getMessage());
            response.putObject("error")
                .put("code", -32603)
                .put("message", "Internal error: " + e.getMessage());
        }

        try {
            emitter.send(SseEmitter.event()
                .name("message")
                .data(response, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.error("Failed to send SSE response", e);
        }

        return ResponseEntity.accepted().build();
    }

    private void handleInitialize(ObjectNode response) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode tools = objectMapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "ai-agent-tools");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        response.set("result", result);
    }

    private void handleToolsList(ObjectNode response) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode toolsArray = objectMapper.createArrayNode();

        addTool(toolsArray, "get_weather", "Get current weather for a city",
            Map.of("city", Map.of("type", "string", "description", "City name (e.g., London)")));
        addTool(toolsArray, "get_news", "Get latest news headlines for a topic",
            Map.of("topic", Map.of("type", "string", "description", "Topic name (e.g., technology)")));
        addTool(toolsArray, "calculate", "Evaluate mathematical expressions",
            Map.of("expression", Map.of("type", "string", "description", "Math expression (e.g., 2 + 2)")));
        addTool(toolsArray, "query_database", "Query the knowledge base for information",
            Map.of("query", Map.of("type", "string", "description", "Search query")));
        addTool(toolsArray, "rag_search", "Search uploaded documents for relevant information",
            Map.of("query", Map.of("type", "string", "description", "Search query (e.g., 'What is the refund policy?')")));
        addTool(toolsArray, "upload_document", "Upload a document (PDF, DOCX, TXT, etc.) for RAG search. Provide filename and base64-encoded content",
            Map.of(
                "filename", Map.of("type", "string", "description", "Filename with extension (e.g., document.pdf)"),
                "content", Map.of("type", "string", "description", "Base64-encoded file content"),
                "contentType", Map.of("type", "string", "description", "MIME type (optional, e.g., application/pdf)")
            ));

        result.set("tools", toolsArray);
        response.set("result", result);
    }

    private void addTool(ArrayNode toolsArray, String name, String description,
                         Map<String, Map<String, String>> inputSchema) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();

        inputSchema.forEach((key, value) -> {
            ObjectNode prop = objectMapper.createObjectNode();
            prop.put("type", value.get("type"));
            prop.put("description", value.get("description"));
            properties.set(key, prop);
        });

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        inputSchema.keySet().forEach(required::add);
        schema.set("required", required);

        tool.set("inputSchema", schema);
        toolsArray.add(tool);
    }

    private void handleToolsCall(JsonNode params, ObjectNode response) {
        String toolName = params.get("name").asText();
        JsonNode arguments = params.get("arguments");

        String result = switch (toolName) {
            case "get_weather" -> weatherTool.execute(arguments.get("city").asText());
            case "get_news" -> newsTool.execute(arguments.get("topic").asText());
            case "calculate" -> calculatorTool.execute(arguments.get("expression").asText());
            case "query_database" -> databaseTool.execute(arguments.get("query").asText());
            case "rag_search" -> ragTool.execute(arguments.get("query").asText());
            case "upload_document" -> handleUpload(arguments);
            default -> "Unknown tool: " + toolName;
        };

        ObjectNode resultNode = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", result);
        content.add(textContent);
        resultNode.set("content", content);

        response.set("result", resultNode);
    }

    private String handleUpload(JsonNode arguments) {
        String filename = arguments.get("filename").asText();
        String base64Content = arguments.get("content").asText();
        String contentType = arguments.has("contentType")
                ? arguments.get("contentType").asText() : detectContentType(filename);

        try {
            byte[] decoded = Base64.getDecoder().decode(base64Content);
            MultipartFile file = new ByteArrayMultipartFile("file", filename, contentType, decoded);
            var info = ingestionService.ingest(file);
            return "Uploaded '%s' successfully (%d chunks)".formatted(info.getFilename(), info.getChunks());
        } catch (IllegalArgumentException e) {
            return "Error: Invalid base64 content";
        } catch (IOException e) {
            log.error("Upload failed", e);
            return "Error uploading document: " + e.getMessage();
        }
    }

    private String detectContentType(String filename) {
        String name = filename.toLowerCase();
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".md")) return "text/markdown";
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html";
        return "application/octet-stream";
    }

    private static class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public Resource getResource() { return new ByteArrayResource(content); }
        @Override public void transferTo(File dest) throws IOException { java.nio.file.Files.write(dest.toPath(), content); }
        @Override public void transferTo(Path dest) throws IOException { java.nio.file.Files.write(dest, content); }
    }
}
