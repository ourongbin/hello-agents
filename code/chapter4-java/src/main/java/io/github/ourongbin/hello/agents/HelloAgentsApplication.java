package io.github.ourongbin.hello.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

@SpringBootApplication(exclude = {
		org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class
})
public class HelloAgentsApplication {

	private static final Logger log = LoggerFactory.getLogger(HelloAgentsApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(HelloAgentsApplication.class, args);
	}

	@Bean
	public CommandLineRunner run(HelloAgentsLLM llmClient) {
		return args -> {
			List<Map<String, String>> exampleMessages = List.of(
					Map.of("role", "system", "content", "You are a helpful assistant that writes Java code."),
					Map.of("role", "user", "content", "用 Java 写一个快速排序算法")
			);

			log.info("--- 调用LLM ---");
			String responseText = llmClient.think(exampleMessages);
			if (responseText != null && !responseText.isEmpty()) {
				log.info("\n\n--- 完整模型响应 ---");
				log.info("{}", responseText);
			}
		};
	}

}
