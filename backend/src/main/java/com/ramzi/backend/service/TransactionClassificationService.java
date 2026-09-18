package com.ramzi.backend.service;

import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.ClassificationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Einstiegspunkt für Phase 3: neue Umsätze klassifizieren (Vertrag, Einkommen, Sonstiges).
 * Die eigentliche KI-Klassifikation kommt später; hier wird sie nur angestoßen.
 */
@Service
@Slf4j
public class TransactionClassificationService {

    public void classifyNewTransactions(List<BankTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }
        transactions.forEach(tx -> {
            if (tx.getClassificationStatus() == null) {
                tx.setClassificationStatus(ClassificationStatus.PENDING);
            }
        });
        log.info("Phase-3-Klassifikation angestoßen für {} neue Umsätze", transactions.size());
    }
}
