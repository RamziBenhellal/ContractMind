package com.ramzi.backend.service;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class GoCardlessService {

    private final WebClient webClient;

    @Value("${gocardless.secret.id}")
    private String secretId;

    @Value("${gocardless.secret.key}")
    private String secretKey;

    public GoCardlessService(WebClient.Builder webClientBuilder,
                             @Value("${gocardless.api.url}") String apiUrl) {
        this.webClient = webClientBuilder.baseUrl(apiUrl).build();
    }

    public String getAccessToken(){
        Map<String, String> credentials = Map.of(
                "secret_id", secretId,
                "secret_key", secretKey
        );

        Map response = webClient.post()
                .uri("/token/new/")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(credentials), Map.class)
                .retrieve()
                .bodyToMono(Map.class)
                .block(); // block() macht den Aufruf synchron (wartet auf die Antwort)

        return (String) response.get("access");
    }
}
