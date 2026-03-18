package io.github.ourongbin.hello.agents;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PlanExecutor {

    private static final String EXECUTOR_PROMPT_TEMPLATE = """
            你是一位顶级的AI执行专家。你的任务是严格按照给定的计划，一步步地解决问题。
            你将收到原始问题、完整的计划、以及到目前为止已经完成的步骤和结果。
            请你专注于解决“当前步骤”，并仅输出该步骤的最终答案，不要输出任何额外的解释或对话。

            # 原始问题:
            %s

            # 完整计划:
            %s

            # 历史步骤与结果:
            %s

            # 当前步骤:
            %s

            请仅输出针对“当前步骤”的回答:
            """;

    private final HelloAgentsLLM llmClient;

    public PlanExecutor(HelloAgentsLLM llmClient) {
        this.llmClient = llmClient;
    }

    public String execute(String question, List<String> plan) {
        StringBuilder history = new StringBuilder();
        String finalAnswer = "";

        System.out.println("\n--- 正在执行计划 ---");
        for (int i = 0; i < plan.size(); i++) {
            String step = plan.get(i);
            int stepNum = i + 1;
            System.out.println("\n-> 正在执行步骤 " + stepNum + "/" + plan.size() + ": " + step);

            String planStr = String.join("\n", plan);
            String historyStr = !history.isEmpty() ? history.toString() : "无";

            String prompt = String.format(EXECUTOR_PROMPT_TEMPLATE, question, planStr, historyStr, step);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", prompt)
            );

            String responseText = llmClient.think(messages);
            if (responseText == null) {
                responseText = "";
            }

            history.append("步骤 ").append(stepNum).append(": ").append(step).append("\n")
                   .append("结果: ").append(responseText).append("\n\n");
            finalAnswer = responseText;
            System.out.println("✅ 步骤 " + stepNum + " 已完成，结果: " + finalAnswer);
        }

        return finalAnswer;
    }
}

