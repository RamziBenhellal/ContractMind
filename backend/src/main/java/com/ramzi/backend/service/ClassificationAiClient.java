package com.ramzi.backend.service;

import com.ramzi.backend.dto.ClassificationAiDto.ClassifyRequest;
import com.ramzi.backend.dto.ClassificationAiDto.ClassifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class ClassificationAiClient {

    private final WebClient webClient;
    private final Duration timeout;
    private final int maxAttempts;
    private final Duration backoff;

    public ClassificationAiClient(
            @Value("${ai-service.url}") String baseUrl,
            @Value("${ai-service.timeout-ms:3000}") long timeoutMs,
            @Value("${ai-service.retry.max-attempts:2}") int maxAttempts,
            @Value("${ai-service.retry.backoff-ms:200}") long backoffMs
    ) {
        this.timeout = Duration.ofMillis(timeoutMs);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoff = Duration.ofMillis(backoffMs);
        HttpClient httpClient = HttpClient.create().responseTimeout(this.timeout);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public ClassifyResponse classify(ClassifyRequest request) {
        return webClient.post()
                .uri("/classify-transaction")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ClassifyResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(maxAttempts, backoff)
                        .filter(this::isRetryable)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .block();
    }

    private boolean isRetryable(Throwable error) {
        if (error instanceof TimeoutException || error instanceof WebClientRequestException) {
            return true;
        }
        if (error instanceof WebClientResponseException response) {
            return response.getStatusCode().is5xxServerError();
        }
        return error.getCause() instanceof TimeoutException;
    }
}
