package io.github.ourongbin.hello.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 为本书 "Hello Agents" 定制的LLM客户端。
 * 它用于调用任何兼容OpenAI接口的服务，并默认使用流式响应。
 * 配置项从 application.yaml 的 spring.ai.openai.* 读取。
 */
@Component
public class HelloAgentsLLM {

    private static final Logger log = LoggerFactory.getLogger(HelloAgentsLLM.class);

    private final String model;
    private final OpenAiChatModel chatModel;

    /**
     * Spring 自动注入构造函数。
     * 手动构建 OpenAiApi，并将 completionsPath 设为 /chat/completions，
     * 以兼容不带 /v1 前缀的第三方 OpenAI 兼容接口。
     *
     * @param apiKey  spring.ai.openai.api-key
     * @param baseUrl spring.ai.openai.base-url
     * @param model   spring.ai.openai.chat.options.model
     */
    @Autowired
    public HelloAgentsLLM(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.model = model;

        // 配置 300 秒读取超时，以支持 Thinking 模型长时间推理
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(300));
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(new ReactorClientHttpRequestFactory(httpClient));

        // 配置 WebClient，增加缓冲区大小到 10MB，防止长文本截断
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(clientDefaultCodecsConfigurer -> clientDefaultCodecsConfigurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        WebClient.Builder webClientBuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .completionsPath("/chat/completions")
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();

        this.chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .build();
    }

    /**
     * 调用大语言模型进行思考，并返回其响应。
     * 使用流式输出，实时打印内容，最终返回完整字符串。
     *
     * @param messages    对话消息列表，每条消息包含 "role" 和 "content"
     * @param temperature 温度参数，控制输出随机性
     * @return 模型完整响应文本，发生错误时返回 null
     */
    public String think(List<Map<String, String>> messages, double temperature) {
        log.info("🧠 正在调用 {} 模型...", model);
        try {
            List<Message> springMessages = convertMessages(messages);

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(this.model)
                    .temperature(temperature)
                    .maxTokens(8192) // 显式设置较大的 maxTokens，防止模型因默认限制提前结束
                    .build();

            Prompt prompt = new Prompt(springMessages, options);

            // 处理流式响应
            log.info("✅ 大语言模型响应成功:");
            StringBuilder collectedContent = new StringBuilder();

            Flux<ChatResponse> responseFlux = chatModel.stream(prompt);
            // 使用 doOnNext + blockLast 代替 toStream()，避免 Reactor 线程死锁
            responseFlux.doOnNext(chunk ->
                    {
                        for (var generation : chunk.getResults()) {
                            // 1. 优先尝试从 metadata 中获取 reasoningContent（Thinking 模型思考内容）
                            var metadata = generation.getOutput().getMetadata();
                            Object reasoning = metadata.get("reasoningContent");
                            if (reasoning != null) {
                                String reasoningText = reasoning.toString();
                                if (!reasoningText.isEmpty()) {
                                    System.out.print(reasoningText);
                                    System.out.flush();
                                    collectedContent.append(reasoningText);
                                }
                            }
                            // 2. 同时收集正式 content（非 Thinking 模型或 Thinking 结束后的最终回答）
                            String text = generation.getOutput().getText();
                            if (text != null && !text.isEmpty()) {
                                System.out.print(text);
                                System.out.flush();
                                collectedContent.append(text);
                            }
                        }
                    })
                    .doOnError(error -> log.error("❌ 流式读取发生异常", error))
                    .doOnComplete(() -> log.info("✅ 流式读取正常结束"))
                    .blockLast();

            return collectedContent.toString();

        } catch (Exception e) {
            log.error("❌ 调用LLM API时发生错误", e);
            return null;
        }
    }

    /**
     * 使用默认 temperature=0 调用模型。
     */
    public String think(List<Map<String, String>> messages) {
        return think(messages, 0.0);
    }

    /**
     * 将通用消息格式（Map列表）转换为 Spring AI 的 Message 列表。
     */
    private List<Message> convertMessages(List<Map<String, String>> messages) {
        List<Message> result = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");
            switch (role) {
                case "system" -> result.add(new SystemMessage(content));
                case "assistant" -> result.add(new AssistantMessage(content));
                default -> result.add(new UserMessage(content));
            }
        }
        return result;
    }
}
