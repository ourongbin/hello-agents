package io.github.ourongbin.hello.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个简单的短期记忆模块，用于存储智能体的行动与反思轨迹。
 */
public class Memory {
    private final List<Map<String, String>> records;

    public Memory() {
        this.records = new ArrayList<>();
    }

    /**
     * 向记忆中添加一条新记录。
     *
     * @param recordType 记录的类型 ('execution' 或 'reflection')。
     * @param content    记录的具体内容 (例如，生成的代码或反思的反馈)。
     */
    public void addRecord(String recordType, String content) {
        Map<String, String> record = new HashMap<>();
        record.put("type", recordType);
        record.put("content", content);
        this.records.add(record);
        System.out.println("📝 记忆已更新，新增一条 '" + recordType + "' 记录。");
    }

    /**
     * 将所有记忆记录格式化为一个连贯的字符串文本，用于构建提示词。
     */
    public String getTrajectory() {
        StringBuilder trajectory = new StringBuilder();
        for (Map<String, String> record : records) {
            if ("execution".equals(record.get("type"))) {
                trajectory.append("--- 上一轮尝试 (代码) ---\n")
                          .append(record.get("content"))
                          .append("\n\n");
            } else if ("reflection".equals(record.get("type"))) {
                trajectory.append("--- 评审员反馈 ---\n")
                          .append(record.get("content"))
                          .append("\n\n");
            }
        }
        return trajectory.toString().trim();
    }

    /**
     * 获取最近一次的执行结果 (例如，最新生成的代码)。
     */
    public String getLastExecution() {
        for (int i = records.size() - 1; i >= 0; i--) {
            Map<String, String> record = records.get(i);
            if ("execution".equals(record.get("type"))) {
                return record.get("content");
            }
        }
        return null;
    }
}

