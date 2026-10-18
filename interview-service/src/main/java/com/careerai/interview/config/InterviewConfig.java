package com.careerai.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties({InterviewConfig.OllamaProperties.class, InterviewConfig.RagProperties.class,
        InterviewConfig.InterviewProperties.class})
public class InterviewConfig {

    @ConfigurationProperties(prefix = "ollama")
    public record OllamaProperties(String baseUrl, String chatModel, String embedModel, boolean think,
                                   int numCtx, double temperature, int seed, String keepAlive,
                                   int connectTimeoutSeconds, int readTimeoutSeconds) { }

    @ConfigurationProperties(prefix = "rag")
    public record RagProperties(String knowledgePath, String promptVersion, int questionsTopK, int feedbackTopK,
                                int rrfK, double mmrLambda, int embedBatchSize) { }

    @ConfigurationProperties(prefix = "interview")
    public record InterviewProperties(String compatServiceUrl, int maxCvChars, int questionCountDefault,
                                      int questionCountMin, int questionCountMax, int answerMaxChars) { }

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }

    @Bean(name = "generationExecutor")
    public ThreadPoolTaskExecutor generationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("interview-gen-");
        executor.initialize();
        return executor;
    }
}
