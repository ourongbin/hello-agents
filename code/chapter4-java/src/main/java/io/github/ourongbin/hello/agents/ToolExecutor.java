package io.github.ourongbin.hello.agents;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolExecutor {

    private static class ToolInfo {
        String description;
        Function<Map<String, Object>, String> func;

        ToolInfo(String description, Function<Map<String, Object>, String> func) {
            this.description = description;
            this.func = func;
        }
    }

    private final Map<String, ToolInfo> tools = new HashMap<>();

    /**
     * 向工具箱中注册一个新工具。
     */
    public void registerTool(String name, String description, Function<Map<String, Object>, String> func) {
        if (tools.containsKey(name)) {
            System.out.println("警告：工具 '" + name + "' 已存在，将被覆盖。");
        }
        tools.put(name, new ToolInfo(description, func));
        System.out.println("工具 '" + name + "' 已注册。");
    }

    /**
     * 根据名称获取一个工具的执行函数。
     */
    public Function<Map<String, Object>, String> getTool(String name) {
        ToolInfo info = tools.get(name);
        return info != null ? info.func : null;
    }

    /**
     * 获取所有可用工具的格式化描述字符串。
     */
    public String getAvailableTools() {
        return tools.entrySet().stream()
                .map(entry -> "- " + entry.getKey() + ": " + entry.getValue().description)
                .collect(Collectors.joining("\n"));
    }

    public static void main(String[] args) {
        // 1. 初始化工具执行器
        ToolExecutor toolExecutor = new ToolExecutor();

        // 2. 注册我们的实战搜索工具
        // 注意：这里为了演示，我们手动创建一个 Tools 实例。在实际的 Spring Boot 应用中，它会被注入。
        RestClient.Builder builder = RestClient.builder();
        Tools toolsInstance = new Tools(builder);

        String searchDescription = "一个网页搜索引擎。当你需要回答关于时事、事实以及在你的知识库中找不到的信息时，应使用此工具。";
        toolExecutor.registerTool("Search", searchDescription, toolsInstance::search);

        // 3. 打印可用的工具
        System.out.println("\n--- 可用的工具 ---");
        System.out.println(toolExecutor.getAvailableTools());

        // 4. 智能体的Action调用，这次我们问一个实时性的问题
        System.out.println("\n--- 执行 Action: Search['英伟达最新的GPU型号是什么'] ---");
        String toolName = "Search";
        Map<String, Object> toolInput = new HashMap<>();
        toolInput.put("query", "英伟达最新的GPU型号是什么");

        Function<Map<String, Object>, String> toolFunction = toolExecutor.getTool(toolName);
        if (toolFunction != null) {
            String observation = toolFunction.apply(toolInput);
            System.out.println("--- 观察 (Observation) ---");
            System.out.println(observation);
        } else {
            System.out.println("错误：未找到名为 '" + toolName + "' 的工具。");
        }
    }
}

