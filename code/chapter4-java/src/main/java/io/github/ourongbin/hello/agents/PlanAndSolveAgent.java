package io.github.ourongbin.hello.agents;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlanAndSolveAgent {

    private final HelloAgentsLLM llmClient;
    private final Planner planner;
    private final PlanExecutor executor;

    public PlanAndSolveAgent(HelloAgentsLLM llmClient, Planner planner, PlanExecutor executor) {
        this.llmClient = llmClient;
        this.planner = planner;
        this.executor = executor;
    }

    public void run(String question) {
        System.out.println("\n--- 开始处理问题 ---\n问题: " + question);
        List<String> plan = planner.plan(question);
        if (plan == null || plan.isEmpty()) {
            System.out.println("\n--- 任务终止 --- \n无法生成有效的行动计划。");
            return;
        }
        System.out.println("✅ 计划已生成:\n" + plan);
        String finalAnswer = executor.execute(question, plan);
        System.out.println("\n--- 任务完成 ---\n最终答案: " + finalAnswer);
    }

    public static void main(String[] args) {
        try {
            HelloAgentsLLM llmClient = new HelloAgentsLLM(
                    System.getenv("LLM_API_KEY"),
                    System.getenv("LLM_BASE_URL"),
                    System.getenv("LLM_MODEL_ID")
            );
            Planner planner = new Planner(llmClient);
            PlanExecutor executor = new PlanExecutor(llmClient);
            PlanAndSolveAgent agent = new PlanAndSolveAgent(llmClient, planner, executor);

            String question = "一个水果店周一卖出了15个苹果。周二卖出的苹果数量是周一的两倍。周三卖出的数量比周二少了5个。请问这三天总共卖出了多少个苹果？";
            agent.run(question);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}

