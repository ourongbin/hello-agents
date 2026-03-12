package io.github.ourongbin.hello.agents;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class Tools {

    @Value("${SERPAPI_API_KEY:}")
    private String serpApiKey;

    private final RestClient restClient;

    public Tools(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String search(Map<String, Object> params) {
        String query = params != null ? String.valueOf(params.get("query")) : "";
        if (query == null || query.trim().isEmpty() || "null".equals(query)) {
            return "错误：缺少必要的参数 'query'。";
        }
        System.out.println("\n🔍 正在执行 [SerpApi] 网页搜索: " + query + "\n");
        try {
            if (serpApiKey == null || serpApiKey.isEmpty()) {
                // 尝试从系统环境变量获取
                serpApiKey = System.getenv("SERPAPI_API_KEY");
                if (serpApiKey == null || serpApiKey.isEmpty()) {
                    return "错误：SERPAPI_API_KEY 未在环境变量中配置。";
                }
            }

            @SuppressWarnings("unchecked") Map<String, Object> results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("serpapi.com")
                            .path("/search")
                            .queryParam("engine", "google")
                            .queryParam("q", query)
                            .queryParam("api_key", serpApiKey)
                            .queryParam("gl", "cn")
                            .queryParam("hl", "zh-cn")
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (results == null) {
                return "对不起，没有找到关于 '" + query + "' 的信息。";
            }

            if (results.containsKey("answer_box_list")) {
                List<?> answerBoxList = (List<?>) results.get("answer_box_list");
                StringBuilder sb = new StringBuilder();
                for (Object item : answerBoxList) {
                    sb.append(item.toString()).append("\n");
                }
                return sb.toString().trim();
            }
            if (results.containsKey("answer_box")) {
                Map<String, Object> answerBox = (Map<String, Object>) results.get("answer_box");
                if (answerBox.containsKey("answer")) {
                    return String.valueOf(answerBox.get("answer"));
                }
            }
            if (results.containsKey("knowledge_graph")) {
                Map<String, Object> knowledgeGraph = (Map<String, Object>) results.get("knowledge_graph");
                if (knowledgeGraph.containsKey("description")) {
                    return String.valueOf(knowledgeGraph.get("description"));
                }
            }
            if (results.containsKey("organic_results")) {
                List<Map<String, Object>> organicResults = (List<Map<String, Object>>) results.get("organic_results");
                if (organicResults != null && !organicResults.isEmpty()) {
                    StringBuilder snippets = new StringBuilder();
                    for (int i = 0; i < Math.min(3, organicResults.size()); i++) {
                        Map<String, Object> res = organicResults.get(i);
                        snippets.append("[").append(i + 1).append("] ")
                                .append(res.getOrDefault("title", "")).append("\n")
                                .append(res.getOrDefault("snippet", "")).append("\n\n");
                    }
                    return snippets.toString().trim();
                }
            }

            return "对不起，没有找到关于 '" + query + "' 的信息。";

        } catch (Exception e) {
            return "搜索时发生错误: " + e.getMessage();
        }
    }
}

