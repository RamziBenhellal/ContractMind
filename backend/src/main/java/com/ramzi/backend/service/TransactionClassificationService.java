package com.ramzi.backend.service;

import com.ramzi.backend.dto.ClassificationAiDto.ClassifyRequest;
import com.ramzi.backend.dto.ClassificationAiDto.ClassifyResponse;
import com.ramzi.backend.dto.ClassificationAiDto.ExistingContract;
import com.ramzi.backend.dto.ClassificationAiDto.ExistingIncome;
import com.ramzi.backend.dto.ClassificationAiDto.ExistingRule;
import com.ramzi.backend.dto.ClassificationAiDto.HistoryTransaction;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.ClassificationStatus;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.Income;
import com.ramzi.backend.entity.TransactionClassification;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.event.ClassificationReviewNeededEvent;
import com.ramzi.backend.repository.BankTransactionRepository;
import com.ramzi.backend.repository.ClassificationRuleRepository;
import com.ramzi.backend.repository.ContractRepository;
import com.ramzi.backend.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionClassificationService {

    private final ClassificationAiClient aiClient;
    private final BankTransactionRepository transactionRepository;
    private final ContractRepository contractRepository;
    private final IncomeRepository incomeRepository;
    private final ClassificationRuleRepository ruleRepository;
    private final BankCalendarService calendarService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void classifyNewTransactions(List<BankTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }
        transactions.forEach(this::classifyOne);
    }

    private void classifyOne(BankTransaction incoming) {
        BankTransaction tx = incoming.getId() != null
                ? transactionRepository.findById(incoming.getId()).orElse(incoming)
                : incoming;
        User user = tx.getBankAccount().getUser();

        try {
            ClassifyResponse response = aiClient.classify(buildRequest(tx, user));
            applyAiResult(tx, user, response);
        } catch (Exception e) {
            log.warn("KI-Klassifikation fehlgeschlagen für Umsatz {}: {}", tx.getId(), e.getMessage());
            if (tx.getClassificationStatus() == null) {
                tx.setClassificationStatus(ClassificationStatus.PENDING);
            }
        }
        transactionRepository.save(tx);
    }

    private void applyAiResult(BankTransaction tx, User user, ClassifyResponse response) {
        if (response == null || response.suggestedAction() == null) {
            tx.setClassificationStatus(ClassificationStatus.PENDING);
            return;
        }
        tx.setClassification(response.classification() != null
                ? response.classification()
                : TransactionClassification.UNCLASSIFIED);
        tx.setConfidenceScore(response.confidence());

        switch (response.suggestedAction()) {
            case AUTO_CALENDAR -> applyAutoCalendar(tx, user, response);
            case SUGGEST_NEW_CONTRACT -> markForReview(tx, user, true);
            case ASK_USER -> markForReview(tx, user, false);
        }
    }

    private void applyAutoCalendar(BankTransaction tx, User user, ClassifyResponse response) {
        if (response.matchedEntityId() == null) {
            markForReview(tx, user, false);
            return;
        }
        if (tx.getClassification() == TransactionClassification.INCOME) {
            Income income = incomeRepository.findById(response.matchedEntityId())
                    .filter(item -> item.getUser().getId().equals(user.getId()))
                    .orElse(null);
            if (income == null) {
                markForReview(tx, user, false);
                return;
            }
            tx.setLinkedIncome(income);
            tx.setLinkedContract(null);
            calendarService.ensureIncomeEntry(user, income, tx.getBookingDate());
        } else {
            Contract contract = contractRepository.findById(response.matchedEntityId())
                    .filter(item -> item.getUser().getId().equals(user.getId()))
                    .orElse(null);
            if (contract == null) {
                markForReview(tx, user, false);
                return;
            }
            tx.setLinkedContract(contract);
            tx.setLinkedIncome(null);
            tx.setClassification(TransactionClassification.CONTRACT);
            calendarService.ensureContractEntry(user, contract, tx.getBookingDate());
        }
        tx.setClassificationStatus(ClassificationStatus.CLASSIFIED);
        tx.setSuggestNewContract(false);
    }

    private void markForReview(BankTransaction tx, User user, boolean suggestNewContract) {
        tx.setClassificationStatus(ClassificationStatus.PENDING_REVIEW);
        tx.setSuggestNewContract(suggestNewContract);
        eventPublisher.publishEvent(new ClassificationReviewNeededEvent(
                user.getId(),
                user.getEmail(),
                tx.getId(),
                suggestNewContract,
                firstNonBlank(tx.getCounterpartyName(), tx.getPurpose(), "Umsatz"),
                tx.getAmount()
        ));
    }

    private ClassifyRequest buildRequest(BankTransaction tx, User user) {
        Long userId = user.getId();
        List<ExistingContract> contracts = contractRepository.findByUser_Id(userId).stream()
                .map(contract -> new ExistingContract(
                        contract.getId(),
                        contract.getProvider(),
                        contract.getMonthlyCost(),
                        null,
                        contract.getProvider()
                ))
                .toList();
        List<ExistingIncome> incomes = incomeRepository.findByUser_Id(userId).stream()
                .map(income -> new ExistingIncome(income.getId(), income.getSource(), income.getAmount()))
                .toList();
        List<ExistingRule> rules = ruleRepository.findByUser_Id(userId).stream()
                .map(rule -> new ExistingRule(
                        rule.getMatchType().name(),
                        rule.getMatchValue(),
                        rule.getTargetType().name(),
                        rule.getTargetEntityId()
                ))
                .toList();
        List<HistoryTransaction> history = transactionRepository.findByBankAccount_User_Id(userId).stream()
                .filter(item -> tx.getId() == null || !item.getId().equals(tx.getId()))
                .map(item -> new HistoryTransaction(
                        item.getAmount(),
                        item.getCounterpartyIban(),
                        item.getBookingDate(),
                        item.getLinkedContract() != null ? item.getLinkedContract().getId() : null
                ))
                .toList();
        return new ClassifyRequest(
                tx.getAmount(),
                tx.getPurpose(),
                tx.getCounterpartyName(),
                tx.getCounterpartyIban(),
                tx.getBookingDate(),
                contracts,
                incomes,
                rules,
                history
        );
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
