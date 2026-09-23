package com.ramzi.backend.controller;

import com.ramzi.backend.dto.BankConnectionDto.*;
import com.ramzi.backend.service.BankConnectionOrchestrationService;
import com.ramzi.backend.service.fints.FinTsModels;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequiredArgsConstructor
@Validated
public class BankConnectionController {

    private final BankConnectionOrchestrationService orchestrationService;

    @Value("${fints.internal-token:dev-internal}")
    private String internalToken;

    @GetMapping("/api/banks/search")
    public ResponseEntity<List<BankSearchItem>> searchBanks(
            @RequestParam @NotBlank @Size(min = 2) String query) {
        return ResponseEntity.ok(orchestrationService.searchBanks(query));
    }

    @PostMapping("/api/bank-connections")
    public ResponseEntity<StartConnectionResponse> startConnection(
            @Valid @RequestBody StartConnectionRequest request,
            Principal principal) {
        return ResponseEntity.ok(orchestrationService.startConnection(principal.getName(), request));
    }

    @PostMapping("/api/bank-connections/{id}/tan-method")
    public ResponseEntity<SelectTanMethodResponse> selectTanMethod(
            @PathVariable UUID id,
            @Valid @RequestBody SelectTanMethodRequest request,
            Principal principal) {
        return ResponseEntity.ok(orchestrationService.selectTanMethod(principal.getName(), id, request));
    }

    @PostMapping("/api/bank-connections/{id}/confirm-tan")
    public ResponseEntity<List<ConnectedAccountDto>> confirmTan(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmTanRequest request,
            Principal principal) {
        return ResponseEntity.ok(orchestrationService.confirmTan(principal.getName(), id, request));
    }

    public record IngestRequest(String pythonSessionId, List<FinTsModels.SyncedAccount> accounts) {}

    @PostMapping("/api/internal/fints/transactions")
    public ResponseEntity<Void> ingestFromPython(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody IngestRequest request) {
        if (internalToken == null || !internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        orchestrationService.ingestFromPython(
                request.pythonSessionId(),
                new FinTsModels.SyncResponse(request.accounts() == null ? List.of() : request.accounts())
        );
        return ResponseEntity.accepted().build();
    }
}
