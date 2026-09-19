package com.ramzi.backend.service.fints;

import com.ramzi.backend.exception.BankConnectionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PythonFinTsClient implements FinTsClient {

    private final WebClient webClient;

    public PythonFinTsClient(@Value("${fints.service.url}") String baseUrl) {
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(60));
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<FinTsModels.BankInfo> searchBanks(String query) {
        try {
            List<FinTsModels.BankInfo> result = webClient.get()
                    .uri(uri -> uri.path("/fints/banks/search").queryParam("query", query).build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<FinTsModels.BankInfo>>() {})
                    .block();
            return result != null ? result : List.of();
        } catch (WebClientRequestException e) {
            throw BankConnectionException.searchFailed();
        } catch (WebClientResponseException e) {
            throw mapError(e, "SEARCH_FAILED");
        }
    }

    @Override
    public FinTsModels.SessionStartResponse startSession(String blz, String loginId, String pin) {
        try {
            FinTsModels.SessionStartResponse response = webClient.post()
                    .uri("/fints/sessions")
                    .bodyValue(Map.of("blz", blz, "loginId", loginId, "pin", pin))
                    .retrieve()
                    .bodyToMono(FinTsModels.SessionStartResponse.class)
                    .block();
            if (response == null || response.sessionId() == null) {
                throw BankConnectionException.unknown("Python-FinTS-Dienst hat keine Session geliefert.");
            }
            return response;
        } catch (WebClientRequestException e) {
            throw BankConnectionException.bankUnreachable();
        } catch (WebClientResponseException e) {
            throw mapError(e, "INVALID_PIN");
        }
    }

    @Override
    public FinTsModels.SelectTanResponse selectTanMethod(String sessionId, String tanMethodId) {
        try {
            FinTsModels.SelectTanResponse response = webClient.post()
                    .uri("/fints/sessions/{id}/tan-method", sessionId)
                    .bodyValue(Map.of("tanMethodId", tanMethodId != null ? tanMethodId : ""))
                    .retrieve()
                    .bodyToMono(FinTsModels.SelectTanResponse.class)
                    .block();
            return response != null ? response : new FinTsModels.SelectTanResponse(null, false, null);
        } catch (WebClientRequestException e) {
            throw BankConnectionException.bankUnreachable();
        } catch (WebClientResponseException e) {
            throw mapError(e, "TAN_INVALID");
        }
    }

    @Override
    public List<FinTsModels.FinTsAccount> confirmTan(String sessionId, String tanMethodId, String tan) {
        try {
            FinTsModels.ConfirmTanResponse response = webClient.post()
                    .uri("/fints/sessions/{id}/confirm-tan", sessionId)
                    .bodyValue(Map.of(
                            "tanMethodId", tanMethodId != null ? tanMethodId : "",
                            "tan", tan != null ? tan : ""
                    ))
                    .retrieve()
                    .bodyToMono(FinTsModels.ConfirmTanResponse.class)
                    .block();
            if (response == null || response.accounts() == null) {
                return List.of();
            }
            return response.accounts();
        } catch (WebClientRequestException e) {
            throw BankConnectionException.bankUnreachable();
        } catch (WebClientResponseException e) {
            throw mapError(e, "TAN_INVALID");
        }
    }

    @Override
    public FinTsModels.SyncResponse sync(String blz, String loginId, String pin) {
        try {
            FinTsModels.SyncResponse response = webClient.post()
                    .uri("/fints/sync")
                    .bodyValue(Map.of("blz", blz, "loginId", loginId, "pin", pin))
                    .retrieve()
                    .bodyToMono(FinTsModels.SyncResponse.class)
                    .block();
            return response != null ? response : new FinTsModels.SyncResponse(List.of());
        } catch (WebClientRequestException e) {
            throw BankConnectionException.bankUnreachable();
        } catch (WebClientResponseException e) {
            throw mapError(e, "BANK_UNREACHABLE");
        }
    }

    private BankConnectionException mapError(WebClientResponseException e, String fallbackCode) {
        String body = e.getResponseBodyAsString();
        String bodyCode = readBodyCode(body);
        String bodyMessage = readBodyMessage(body);
        if ("INVALID_PIN".equals(bodyCode)) return BankConnectionException.invalidPin();
        if ("TAN_INVALID".equals(bodyCode)) return BankConnectionException.tanInvalid();
        if ("TAN_EXPIRED".equals(bodyCode)) return BankConnectionException.tanExpired();
        if ("BANK_UNREACHABLE".equals(bodyCode)) return BankConnectionException.bankUnreachable(bodyMessage);
        if ("SEARCH_FAILED".equals(bodyCode)) return BankConnectionException.searchFailed();

        HttpStatusCode status = e.getStatusCode();
        if (status.value() == 401 || status.value() == 403) return BankConnectionException.invalidPin();
        if (status.value() == 410) return BankConnectionException.tanExpired();
        if (status.value() == 502 || status.value() == 503 || status.value() == 504) {
            return BankConnectionException.bankUnreachable(bodyMessage);
        }
        if ("SEARCH_FAILED".equals(fallbackCode)) return BankConnectionException.searchFailed();
        if ("INVALID_PIN".equals(fallbackCode)) return BankConnectionException.invalidPin();
        if ("TAN_INVALID".equals(fallbackCode)) return BankConnectionException.tanInvalid();
        return BankConnectionException.bankUnreachable();
    }

    private static final Pattern BODY_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern BODY_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private String readBodyCode(String body) {
        if (body == null || body.isBlank()) return null;
        Matcher matcher = BODY_CODE.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String readBodyMessage(String body) {
        if (body == null || body.isBlank()) return null;
        Matcher matcher = BODY_MESSAGE.matcher(body);
        if (!matcher.find()) return null;
        return matcher.group(1).replace("\\\"", "\"");
    }
}
