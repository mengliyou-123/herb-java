package org.herb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 百草居与 tcm_merge Agent 的内部适配器。
 *
 * <p>Agent 自己负责路由、RAG、知识图谱和安全层；本类只负责 HTTP/SSE
 * 协议适配，不把 Agent 地址或模型密钥暴露给浏览器。</p>
 */
@Component
public class TcmAgentClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${baicaoju.agent.base-url:http://localhost:7862}")
    private String baseUrl;

    @Value("${baicaoju.agent.request-timeout-ms:180000}")
    private long requestTimeoutMs;

    public TcmAgentClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 调用 Agent 的 JSON 接口，供非流式问诊接口使用。
     */
    public String query(String question) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of("q", normalizeQuestion(question)));
        HttpRequest request = HttpRequest.newBuilder(endpoint("/api/query"))
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        ensureSuccess(response.statusCode(), response.body());

        JsonNode result = objectMapper.readTree(response.body());
        return result.path("answer").asText("").trim();
    }

    /**
     * 调用 Agent 的 SSE 接口。tcm_merge 会发送累计文本，本方法转换为增量文本，
     * 以兼容百草居原有的前端 SSE 消费方式。
     */
    public void stream(String question, Consumer<String> onText)
            throws IOException, InterruptedException {
        // /api/query/stream 使用 QueryRequest(JSON)；/query/stream 才是表单接口。
        // 这里调用带 /api 的接口，必须和 Agent 的 JSON 请求模型保持一致。
        String requestBody = objectMapper.writeValueAsString(Map.of("q", normalizeQuestion(question)));
        HttpRequest request = HttpRequest.newBuilder(endpoint("/api/query/stream"))
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream responseBody = response.body()) {
                String errorBody = new String(responseBody.readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException("Agent 请求失败，HTTP " + response.statusCode() + ": " + errorBody);
            }
        }

        String previousCumulativeText = "";
        try (InputStream responseBody = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }

                JsonNode event = parseEvent(data);
                if (event == null || !"text".equals(event.path("type").asText())) {
                    continue;
                }

                String cumulativeText = event.path("data").asText("");
                String delta = toDelta(previousCumulativeText, cumulativeText);
                previousCumulativeText = cumulativeText;
                if (!delta.isEmpty()) {
                    onText.accept(delta);
                }
            }
        }
    }

    private JsonNode parseEvent(String data) throws IOException {
        try {
            return objectMapper.readTree(data);
        } catch (IOException exception) {
            // 忽略无法识别的状态事件，避免单条非业务 SSE 事件中断整次问诊。
            return null;
        }
    }

    private String toDelta(String previous, String current) {
        if (current.isEmpty()) {
            return "";
        }
        if (previous.isEmpty()) {
            return current;
        }
        if (current.startsWith(previous)) {
            return current.substring(previous.length());
        }
        // Agent 当前协议是累计文本；若未来返回非累计事件，避免重复追加旧内容。
        return "";
    }

    private String normalizeQuestion(String question) {
        String value = question == null ? "" : question.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("问诊内容不能为空");
        }
        if (value.length() > 500) {
            throw new IllegalArgumentException("问诊内容不能超过 500 个字符");
        }
        return value;
    }

    private URI endpoint(String path) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        if (normalizedBaseUrl.isEmpty()) {
            throw new IllegalStateException("未配置 tcm_merge Agent 地址");
        }
        return URI.create(normalizedBaseUrl + path);
    }

    private void ensureSuccess(int statusCode, String body) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Agent 请求失败，HTTP " + statusCode + ": " + body);
        }
    }
}
