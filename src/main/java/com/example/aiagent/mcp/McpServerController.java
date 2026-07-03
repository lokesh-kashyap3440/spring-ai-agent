package com.example.aiagent.mcp;

import com.example.aiagent.security.JwtUtil;
import com.example.aiagent.service.DocumentIngestionService;
import com.example.aiagent.tools.Tool;
import com.example.aiagent.tools.ToolRegistry;
import com.example.aiagent.util.ByteArrayMultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/mcp")
public class McpServerController {

    private static final Logger log = LoggerFactory.getLogger(McpServerController.class);
    private static final int MAX_SSE_CONNECTIONS = 100;
    private static final long SSE_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionUsers = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final ToolRegistry toolRegistry;
    private final DocumentIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    public McpServerController(ToolRegistry toolRegistry,
                               DocumentIngestionService ingestionService,
                               ObjectMapper objectMapper, JwtUtil jwtUtil) {
        this.toolRegistry = toolRegistry;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    /**
     * MCP SSE endpoint. MCP clients SHOULD pass an {@code Authorization: Bearer <token>} header.
     * If the header is missing, the connection is still accepted for backward compatibility,
     * but a warning is logged. When provided, the JWT is validated and the associated user
     * is stored with the session.
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestHeader("Authorization") Optional<String> authHeader,
                              HttpServletRequest request) {
        // Atomically check and reserve a connection slot to avoid TOCTOU race
        int current = connectionCount.incrementAndGet();
        if (current > MAX_SSE_CONNECTIONS) {
            connectionCount.decrementAndGet();
            throw new IllegalStateException("Maximum SSE connections reached: " + MAX_SSE_CONNECTIONS);
        }

        String sessionId = UUID.randomUUID().toString();
        String username = "anonymous";

        // Validate JWT if present — lenient for backward compatibility
        if (authHeader.isPresent() && authHeader.get().startsWith("Bearer ")) {
            String token = authHeader.get().substring(7);
            if (jwtUtil.validateToken(token)) {
                username = jwtUtil.extractUsername(token);
                log.info("SSE connect: authenticated user '{}' for session {}", username, sessionId);
            } else {
                log.warn("SSE connect: invalid JWT token from {} (session {})",
                        request.getRemoteAddr(), sessionId);
            }
        } else {
            log.warn("SSE connect without Authorization header from {} (session {}) - "
                    + "MCP clients should pass Authorization: Bearer <token>",
                    request.getRemoteAddr(), sessionId);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(sessionId, emitter);
        sessionUsers.put(sessionId, username);

        Runnable cleanup = () -> {
            emitters.remove(sessionId);
            sessionUsers.remove(sessionId);
            connectionCount.decrementAndGet();
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            log.debug("SSE connection timed out for session: {}", sessionId);
        });
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/message?sessionId=" + sessionId));
        } catch (IOException e) {
            log.error("Failed to send endpoint event", e);
            cleanup.run();
            throw new IllegalStateException("Failed to initialize SSE connection", e);
        }

        log.info("New SSE connection established: session={}, user={}, totalConnections={}",
                sessionId, username, connectionCount.get());
        return emitter;
    }

    /**
     * MCP Streamable HTTP endpoint. MCP clients MUST pass an {@code Authorization: Bearer <token>} header.
     * If the JWT is missing or invalid, a warning is logged but the request is still processed
     * for backward compatibility.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> handleStreamableHttp(@RequestBody ObjectNode request,
                                                       @RequestHeader("Authorization") Optional<String> authHeader,
                                                       HttpServletRequest servletRequest) {
        validateMcpAuthHeader(authHeader, servletRequest);
        ObjectNode response = dispatchJsonRpc(request);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * MCP message endpoint (SSE-based messaging). MCP clients SHOULD pass an
     * {@code Authorization: Bearer <token>} header. If the JWT is missing or invalid,
     * a warning is logged but the request is still processed for backward compatibility.
     */
    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleMessage(@RequestBody ObjectNode request,
                                              @RequestParam String sessionId,
                                              @RequestHeader("Authorization") Optional<String> authHeader,
                                              HttpServletRequest servletRequest) {
        validateMcpAuthHeader(authHeader, servletRequest);

        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            return ResponseEntity.notFound().build();
        }

        ObjectNode response = dispatchJsonRpc(request);

        if (response != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(response, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                log.error("Failed to send SSE response", e);
            }
        }

        return ResponseEntity.accepted().build();
    }

    /**
     * Validates the Authorization header for MCP endpoints.
     * Lenient: logs a warning if missing or invalid, but does not reject the request.
     */
    private void validateMcpAuthHeader(Optional<String> authHeader, HttpServletRequest request) {
        if (authHeader.isEmpty() || !authHeader.get().startsWith("Bearer ")) {
            log.warn("MCP request without valid Authorization header from {} - "
                    + "MCP clients should pass Authorization: Bearer <token>",
                    request.getRemoteAddr());
            return;
        }

        String token = authHeader.get().substring(7);
        if (!jwtUtil.validateToken(token)) {
            log.warn("MCP request with invalid JWT from {}", request.getRemoteAddr());
        }
    }

    private ObjectNode dispatchJsonRpc(ObjectNode request) {
        String method = request.has("method") ? request.get("method").asText() : null;
        JsonNode params = request.has("params") ? request.get("params") : null;
        String id = request.has("id") ? request.get("id").asText() : null;

        log.info("MCP JSON-RPC: method={}, id={}", method, id);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");

        if (id == null) {
            return null;
        }

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

        return response;
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
        serverInfo.put("version", "2.0.0");
        result.set("serverInfo", serverInfo);

        response.set("result", result);
    }

    private void handleToolsList(ObjectNode response) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode toolsArray = objectMapper.createArrayNode();

        for (Tool tool : toolRegistry.getAllTools().values()) {
            String mcpName = toolNameToMcp(tool.getName());
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = tool.getParameterSchema();
            toolsArray.add(buildToolEntry(mcpName, tool.getDescription(), schema));
        }

        // Add the special upload_document tool (not a Tool implementation)
        toolsArray.add(buildUploadDocumentTool());

        result.set("tools", toolsArray);
        response.set("result", result);
    }

    private ObjectNode buildToolEntry(String name, String description, Map<String, Object> schema) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);

        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");

        @SuppressWarnings("unchecked")
        Map<String, Object> propertiesMap = (Map<String, Object>) schema.get("properties");
        ObjectNode properties = objectMapper.createObjectNode();
        if (propertiesMap != null) {
            for (Map.Entry<String, Object> entry : propertiesMap.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> propMap = (Map<String, Object>) entry.getValue();
                ObjectNode prop = objectMapper.createObjectNode();
                propMap.forEach((k, v) -> prop.put(k, v.toString()));
                properties.set(entry.getKey(), prop);
            }
        }
        inputSchema.set("properties", properties);

        @SuppressWarnings("unchecked")
        java.util.List<String> requiredList = (java.util.List<String>) schema.get("required");
        ArrayNode required = objectMapper.createArrayNode();
        if (requiredList != null) {
            requiredList.forEach(required::add);
        }
        inputSchema.set("required", required);

        tool.set("inputSchema", inputSchema);
        return tool;
    }

    private ObjectNode buildUploadDocumentTool() {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "upload_document");
        tool.put("description", "Upload a document (PDF, DOCX, TXT, etc.) for RAG search. Provide filename and base64-encoded content");

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode filenameProp = objectMapper.createObjectNode();
        filenameProp.put("type", "string");
        filenameProp.put("description", "Filename with extension (e.g., document.pdf)");
        properties.set("filename", filenameProp);

        ObjectNode contentProp = objectMapper.createObjectNode();
        contentProp.put("type", "string");
        contentProp.put("description", "Base64-encoded file content");
        properties.set("content", contentProp);

        ObjectNode contentTypeProp = objectMapper.createObjectNode();
        contentTypeProp.put("type", "string");
        contentTypeProp.put("description", "MIME type (optional, e.g., application/pdf)");
        properties.set("contentType", contentTypeProp);

        schema.set("properties", properties);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("filename");
        required.add("content");
        schema.set("required", required);

        tool.set("inputSchema", schema);
        return tool;
    }

    private void handleToolsCall(JsonNode params, ObjectNode response) {
        String mcpName = params.get("name").asText();
        JsonNode arguments = params.get("arguments");

        String result;
        if ("upload_document".equals(mcpName)) {
            result = handleUpload(arguments);
        } else {
            String toolName = mcpToToolName(mcpName);
            Tool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                result = "Unknown tool: " + mcpName;
            } else {
                // Extract the first (and only) property value from arguments
                String input = extractInput(arguments, tool);
                result = tool.execute(input);
            }
        }

        ObjectNode resultNode = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", result);
        content.add(textContent);
        resultNode.set("content", content);

        response.set("result", resultNode);
    }

    private String extractInput(JsonNode arguments, Tool tool) {
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = tool.getParameterSchema();
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        if (required != null && !required.isEmpty()) {
            String firstParam = required.get(0);
            if (arguments.has(firstParam)) {
                return arguments.get(firstParam).asText();
            }
        }
        // Fallback: use the first available field
        if (arguments != null && arguments.fieldNames().hasNext()) {
            return arguments.get(arguments.fieldNames().next()).asText();
        }
        return "";
    }

    private String toolNameToMcp(String toolName) {
        return switch (toolName) {
            case "weather" -> "get_weather";
            case "news" -> "get_news";
            case "calculator" -> "calculate";
            case "knowledge_base" -> "query_knowledge_base";
            default -> toolName;
        };
    }

    private String mcpToToolName(String mcpName) {
        return switch (mcpName) {
            case "get_weather" -> "weather";
            case "get_news" -> "news";
            case "calculate" -> "calculator";
            case "query_knowledge_base" -> "knowledge_base";
            default -> mcpName;
        };
    }

    private String handleUpload(JsonNode arguments) {
        String filename = arguments.get("filename").asText();
        String base64Content = arguments.get("content").asText();
        String contentType = arguments.has("contentType")
                ? arguments.get("contentType").asText() : detectContentType(filename);

        try {
            byte[] decoded = Base64.getDecoder().decode(base64Content);
            MultipartFile file = new ByteArrayMultipartFile("file", filename, contentType, decoded);
            var info = ingestionService.ingest(file, "mcp");
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
}
