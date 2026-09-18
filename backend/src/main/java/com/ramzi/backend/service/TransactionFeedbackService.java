package com.ramzi.backend.service;

import com.ramzi.backend.dto.TransactionFeedbackDto.FeedbackRequest;
import com.ramzi.backend.dto.TransactionFeedbackDto.FeedbackResponse;
import com.ramzi.backend.dto.TransactionFeedbackDto.NewContractData;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.ClassificationMatchType;
import com.ramzi.backend.entity.ClassificationRule;
import com.ramzi.backend.entity.ClassificationStatus;
import com.ramzi.backend.entity.ClassificationTargetType;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.Income;
import com.ramzi.backend.entity.TransactionClassification;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.exception.TransactionClassificationException;
import com.ramzi.backend.repository.BankTransactionRepository;
import com.ramzi.backend.repository.ClassificationRuleRepository;
import com.ramzi.backend.repository.ContractRepository;
import com.ramzi.backend.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionFeedbackService {

    private final BankTransactionRepository transactionRepository;
    private final ContractRepository contractRepository;
    private final IncomeRepository incomeRepository;
    private final ClassificationRuleRepository ruleRepository;
    private final BankCalendarService calendarService;
    private final ClassificationNotificationService notificationService;

    @Transactional
    public FeedbackResponse applyFeedback(String email, Long transactionId, FeedbackRequest request) {
        BankTransaction tx = transactionRepository.findByIdAndBankAccount_User_Email(transactionId, email)
                .orElseThrow(TransactionClassificationException::notFound);
        User user = tx.getBankAccount().getUser();

        switch (request.decision()) {
            case EXISTING_MATCH -> applyExistingMatch(tx, user, request);
            case NEW_CONTRACT -> applyNewContract(tx, user, request);
            case NEW_INCOME -> applyNewIncome(tx, user, request);
            case NORMAL -> applyNormal(tx, request);
        }

        tx.setSuggestNewContract(false);
        BankTransaction saved = transactionRepository.save(tx);
        notificationService.dismiss(email, saved.getId());
        return toResponse(saved);
    }

    private void applyExistingMatch(BankTransaction tx, User user, FeedbackRequest request) {
        if (request.targetEntityId() == null) {
            throw TransactionClassificationException.badRequest("targetEntityId ist für EXISTING_MATCH erforderlich.");
        }
        Contract contract = contractRepository.findById(request.targetEntityId())
                .filter(item -> item.getUser().getId().equals(user.getId()))
                .orElse(null);
        if (contract != null) {
            linkContract(tx, contract);
            learnRule(tx, user, ClassificationTargetType.CONTRACT, contract.getId());
            return;
        }
        Income income = incomeRepository.findById(request.targetEntityId())
                .filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> TransactionClassificationException.badRequest(
                        "Ziel-Entity gehört nicht zum Nutzer oder existiert nicht."));
        linkIncome(tx, income);
        learnRule(tx, user, ClassificationTargetType.INCOME, income.getId());
    }

    private void applyNewContract(BankTransaction tx, User user, FeedbackRequest request) {
        NewContractData data = request.newContractData();
        Contract contract = contractRepository.save(Contract.builder()
                .user(user)
                .provider(firstNonBlank(
                        data != null ? data.provider() : null,
                        tx.getCounterpartyName(),
                        tx.getPurpose(),
                        "Neuer Vertrag"))
                .contractType(firstNonBlank(
                        data != null ? data.contractType() : null,
                        request.category(),
                        "Sonstiges"))
                .monthlyCost(data != null && data.monthlyCost() != null
                        ? data.monthlyCost()
                        : absolute(tx.getAmount()))
                .dueDayOfMonth(data != null && data.dueDayOfMonth() != null
                        ? data.dueDayOfMonth()
                        : dayOfMonth(tx))
                .status("VERIFIED")
                .build());
        linkContract(tx, contract);
        calendarService.ensureContractEntry(user, contract, tx.getBookingDate());
        learnRule(tx, user, ClassificationTargetType.CONTRACT, contract.getId());
    }

    private void applyNewIncome(BankTransaction tx, User user, FeedbackRequest request) {
        NewContractData data = request.newContractData();
        Income income = incomeRepository.save(Income.builder()
                .user(user)
                .source(firstNonBlank(
                        data != null ? data.provider() : null,
                        tx.getCounterpartyName(),
                        tx.getPurpose(),
                        "Neues Einkommen"))
                .amount(data != null && data.monthlyCost() != null
                        ? data.monthlyCost()
                        : absolute(tx.getAmount()))
                .paydayOfMonth(data != null && data.dueDayOfMonth() != null
                        ? data.dueDayOfMonth()
                        : dayOfMonth(tx))
                .build());
        linkIncome(tx, income);
        calendarService.ensureIncomeEntry(user, income, tx.getBookingDate());
        learnRule(tx, user, ClassificationTargetType.INCOME, income.getId());
    }

    private void applyNormal(BankTransaction tx, FeedbackRequest request) {
        tx.setClassification(TransactionClassification.NORMAL);
        tx.setClassificationStatus(ClassificationStatus.CLASSIFIED);
        tx.setLinkedContract(null);
        tx.setLinkedIncome(null);
        if (request.category() != null) {
            tx.setCategory(request.category());
        }
    }

    private void linkContract(BankTransaction tx, Contract contract) {
        tx.setLinkedContract(contract);
        tx.setLinkedIncome(null);
        tx.setClassification(TransactionClassification.CONTRACT);
        tx.setClassificationStatus(ClassificationStatus.CLASSIFIED);
    }

    private void linkIncome(BankTransaction tx, Income income) {
        tx.setLinkedIncome(income);
        tx.setLinkedContract(null);
        tx.setClassification(TransactionClassification.INCOME);
        tx.setClassificationStatus(ClassificationStatus.CLASSIFIED);
    }

    private void learnRule(BankTransaction tx, User user, ClassificationTargetType targetType, Long targetEntityId) {
        String matchValue;
        ClassificationMatchType matchType;
        if (tx.getCounterpartyIban() != null && !tx.getCounterpartyIban().isBlank()) {
            matchType = ClassificationMatchType.IBAN;
            matchValue = tx.getCounterpartyIban().replaceAll("\\s+", "").toUpperCase();
        } else if (tx.getCounterpartyName() != null && !tx.getCounterpartyName().isBlank()) {
            matchType = ClassificationMatchType.MERCHANT_NAME_FUZZY;
            matchValue = tx.getCounterpartyName().trim();
        } else if (tx.getPurpose() != null && !tx.getPurpose().isBlank()) {
            matchType = ClassificationMatchType.MERCHANT_NAME_FUZZY;
            matchValue = tx.getPurpose().trim();
        } else {
            return;
        }
        ruleRepository.save(ClassificationRule.builder()
                .user(user)
                .matchType(matchType)
                .matchValue(matchValue)
                .targetType(targetType)
                .targetEntityId(targetEntityId)
                .learnedFromTransaction(tx)
                .build());
    }

    private static FeedbackResponse toResponse(BankTransaction tx) {
        return new FeedbackResponse(
                tx.getId(),
                tx.getClassification(),
                tx.getClassificationStatus(),
                tx.getLinkedContract() != null ? tx.getLinkedContract().getId() : null,
                tx.getLinkedIncome() != null ? tx.getLinkedIncome().getId() : null,
                tx.getCategory(),
                tx.isSuggestNewContract()
        );
    }

    private static Integer dayOfMonth(BankTransaction tx) {
        return tx.getBookingDate() != null ? tx.getBookingDate().getDayOfMonth() : null;
    }

    private static BigDecimal absolute(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.abs();
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
