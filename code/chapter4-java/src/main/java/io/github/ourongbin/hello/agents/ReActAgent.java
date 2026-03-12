package io.github.ourongbin.hello.agents;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ReActAgent {

    private static final String REACT_PROMPT_TEMPLATE = """
            请注意，你是一个有能力调用外部工具的智能助手，当前日期为 %s。

            可用工具如下：
            %s

            请严格按照以下格式进行回应：

            %s
            
            现在，请开始解决以下问题：
            Question: %s
            History:
            %s
            """;

    private final HelloAgentsLLM llmClient;
    private final ToolExecutor toolExecutor;
    private final int maxSteps;
    private final List<String> history;

    @Autowired
    public ReActAgent(HelloAgentsLLM llmClient, ToolExecutor toolExecutor) {
        this(llmClient, toolExecutor, 5);
    }

    public ReActAgent(HelloAgentsLLM llmClient, ToolExecutor toolExecutor, int maxSteps) {
        this.llmClient = llmClient;
        this.toolExecutor = toolExecutor;
        this.maxSteps = maxSteps;
        this.history = new ArrayList<>();
    }

    public String run(String question) {
        this.history.clear();
        int currentStep = 0;

        while (currentStep < this.maxSteps) {
            currentStep++;
            System.out.println("\n--- 第 " + currentStep + " 步 ---");

            String toolsDesc = this.toolExecutor.getAvailableTools();
            String historyStr = String.join("\n", this.history);

            String stepInfo = """
                    Thought: 你的思考过程，用于分析问题、拆解任务和规划下一步行动。
                    Action: 你决定采取的行动，必须是以下格式之一：
                    - `{{tool_name}}[{{tool_input}}]`：调用一个可用工具。
                    - `Finish[最终答案]`：当你认为已经获得最终答案时。
                    """;
            if (currentStep >= this.maxSteps) {
                stepInfo = """
                    【警告】这是最后一步，你必须使用 `Finish[最终答案]` 格式输出结果，绝对不允许再调用任何其他工具！
                    
                    Thought: 你的思考过程，用于分析问题。
                    Action: `Finish[最终答案]`。
                    """;
                // 在最后一步，清空可用工具列表，从根本上断绝它调用工具的念头
                toolsDesc = "无可用工具。你必须直接输出最终答案。";
            }
//            System.out.println(stepInfo);
            String prompt = String.format(REACT_PROMPT_TEMPLATE, LocalDate.now(), toolsDesc, stepInfo, question, historyStr);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", prompt));

            String responseText = this.llmClient.think(messages);
            log.info(">>>>>>>>>>> 调用LLM >>>>>> \n messages: {} \n\n responseText: {}", messages, responseText);
            if (responseText == null || responseText.isEmpty()) {
                System.out.println("错误：LLM未能返回有效响应。");
                break;
            }

            String[] parsedOutput = parseOutput(responseText);
            String thought = parsedOutput[0];
            String action = parsedOutput[1];

            if (thought != null && !thought.isEmpty()) {
//                System.out.println("🤔 思考: " + thought);
            }
            if (action == null || action.isEmpty()) {
                System.out.println("警告：未能解析出有效的Action，流程终止。");
                break;
            }

            if (action.startsWith("Finish")) {
                // 如果是Finish指令，提取最终答案并结束
                String finalAnswer = parseActionFinish(action);
                System.out.println("🎉 最终答案: " + finalAnswer);
                return finalAnswer;
            }

            String[] parsedAction = parseActionToolUse(action);
            String toolName = parsedAction[0];
            String toolInputStr = parsedAction[1];

            if (toolName == null || toolInputStr == null) {
                this.history.add("观测结果: 无效的Action格式，请检查。");
                continue;
            }

//            System.out.println("🎬 行动: " + toolName + "[" + toolInputStr + "]");
            Function<Map<String, Object>, String> toolFunction = this.toolExecutor.getTool(toolName);

            String observation;
            if (toolFunction != null) {
                Map<String, Object> toolInputMap = new HashMap<>();
                toolInputMap.put("query", toolInputStr);
                observation = toolFunction.apply(toolInputMap);
            } else {
                observation = "错误：未找到名为 '" + toolName + "' 的工具。";
            }

//            System.out.println("👀 观察: " + observation);
            this.history.add("已行动: " + action);
            this.history.add("观测结果: " + observation);
        }

        System.out.println("已达到最大步数，流程终止。");
        return null;
    }

    private String[] parseOutput(String text) {
        String thought = null;
        String action = null;

        // Thought: 匹配到 Action: 或文本末尾
        Pattern thoughtPattern = Pattern.compile("Thought:\\s*(.*?)(?=\\nAction:|$)", Pattern.DOTALL);
        Matcher thoughtMatcher = thoughtPattern.matcher(text);
        if (thoughtMatcher.find()) {
            thought = thoughtMatcher.group(1).trim();
        }

        // Action: 匹配到文本末尾，但要排除大模型自己生成的 Observation: 等后续内容
        Pattern actionPattern = Pattern.compile("Action:\\s*(.*?)(?=\\nObservation:|$)", Pattern.DOTALL);
        Matcher actionMatcher = actionPattern.matcher(text);
        if (actionMatcher.find()) {
            action = actionMatcher.group(1).trim();
            // 进一步清理，如果大模型输出了反引号，去掉它们
            action = action.replaceAll("^`|`$", "").trim();

            // 如果模型输出了多个 Action，只取第一个。但如果是 Finish，里面可能包含换行，不能简单 split
            if (action.contains("\n") && !action.startsWith("Finish")) {
                action = action.split("\n")[0].trim();
            }
        }

        return new String[]{thought, action};
    }

    private String[] parseActionToolUse(String actionText) {
        // 使用非贪婪匹配 .*? 来防止跨行匹配到后面的内容
        Pattern pattern = Pattern.compile("(\\w+)\\[(.*?)]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(actionText);
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        return new String[]{null, null};
    }

    private String parseActionFinish(String actionText) {
        // 使用非贪婪匹配 .*? 并且开启 DOTALL 模式以支持跨行匹配
        Pattern pattern = Pattern.compile("\\w+\\[(.*?)]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(actionText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static void main(String[] args) {
        // 初始化 LLM 客户端
        HelloAgentsLLM llm = new HelloAgentsLLM( System.getenv("LLM_API_KEY"), System.getenv("LLM_BASE_URL"), System.getenv("LLM_MODEL_ID") );

        // 初始化工具执行器
        ToolExecutor toolExecutor = new ToolExecutor();

        // 初始化 Tools
        RestClient.Builder builder = RestClient.builder();
        Tools toolsInstance = new Tools(builder);

        String searchDesc = "一个网页搜索引擎。当你需要回答关于时事、事实以及在你的知识库中找不到的信息时，应使用此工具。";
        toolExecutor.registerTool("Search", searchDesc, toolsInstance::search);

        // 初始化 Agent
        ReActAgent agent = new ReActAgent(llm, toolExecutor);
        String question = "华为最新的手机是哪一款？它的主要卖点是什么？";
        agent.run(question);
    }
}

