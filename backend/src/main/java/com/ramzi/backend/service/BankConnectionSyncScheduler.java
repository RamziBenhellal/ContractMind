package com.ramzi.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "fints.sync.enabled", havingValue = "true", matchIfMissing = true)
public class BankConnectionSyncScheduler {

    private final BankConnectionOrchestrationService orchestrationService;

    /** Alle 6 Stunden: ACTIVE Connections syncen, neue Umsätze speichern, Klassifikation anstoßen. */
    @Scheduled(cron = "${fints.sync.cron:0 0 */6 * * *}")
    public void syncActiveConnections() {
        log.info("Geplanter FinTS-Sync startet");
        orchestrationService.syncAllActiveConnections();
    }
}
