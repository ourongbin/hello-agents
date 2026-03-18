package io.github.ourongbin.hello.agents;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Planner {

    private static final String PLANNER_PROMPT_TEMPLATE = """
            你是一个顶级的AI规划专家。你的任务是将用户提出的复杂问题分解成一个由多个简单步骤组成的行动计划。
            请确保计划中的每个步骤都是一个独立的、可执行的子任务，并且严格按照逻辑顺序排列。
            你的输出必须是一个Python列表，其中每个元素都是一个描述子任务的字符串。

            问题: %s

            请严格按照以下格式输出你的计划，```python与```作为前后缀是必要的:
            ```python
            ["步骤1", "步骤2", "步骤3", ...]
            ```
            """;

    private final HelloAgentsLLM llmClient;

    public Planner(HelloAgentsLLM llmClient) {
        this.llmClient = llmClient;
    }

    public List<String> plan(String question) {
        String prompt = String.format(PLANNER_PROMPT_TEMPLATE, question);
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );

        System.out.println("--- 正在生成计划 ---");
        String responseText = llmClient.think(messages);
        if (responseText == null) {
            responseText = "";
        }
//        System.out.println("✅ 计划已生成:\n" + responseText);

        try {
            // Extract content between ```python and ```
            Pattern pattern = Pattern.compile("```python\\s*(.*?)\\s*```", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(responseText);
            String planStr = "";
            if (matcher.find()) {
                planStr = matcher.group(1).trim();
            } else {
                // Fallback if no ```python block
                planStr = responseText.trim();
            }

            // Parse the Python list string into a Java List<String>
            return parsePythonList(planStr);
        } catch (Exception e) {
            System.out.println("❌ 解析计划时出错: " + e.getMessage());
            System.out.println("原始响应: " + responseText);
            return new ArrayList<>();
        }
    }

    private List<String> parsePythonList(String listStr) {
        List<String> result = new ArrayList<>();
        // Remove brackets
        listStr = listStr.trim();
        if (listStr.startsWith("[")) {
            listStr = listStr.substring(1);
        }
        if (listStr.endsWith("]")) {
            listStr = listStr.substring(0, listStr.length() - 1);
        }

        // Split by comma, but handle quotes properly
        // A simple regex to match strings in quotes
        Pattern stringPattern = Pattern.compile("[\"'](.*?)[\"']");
        Matcher matcher = stringPattern.matcher(listStr);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }
}

