package com.ramzi.backend.controller;

import com.ramzi.backend.dto.BankConnectionDto.*;
import com.ramzi.backend.service.BankConnectionOrchestrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/api/bank-connections/{id}/confirm-tan")
    public ResponseEntity<List<ConnectedAccountDto>> confirmTan(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmTanRequest request,
            Principal principal) {
        return ResponseEntity.ok(orchestrationService.confirmTan(principal.getName(), id, request));
    }
}
