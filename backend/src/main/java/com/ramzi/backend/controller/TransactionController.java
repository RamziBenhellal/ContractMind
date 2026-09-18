package com.ramzi.backend.controller;

import com.ramzi.backend.dto.ClassificationNotificationDto;
import com.ramzi.backend.dto.TransactionFeedbackDto.FeedbackRequest;
import com.ramzi.backend.dto.TransactionFeedbackDto.FeedbackResponse;
import com.ramzi.backend.dto.TransactionFeedbackDto.PendingReviewDto;
import com.ramzi.backend.entity.ClassificationStatus;
import com.ramzi.backend.repository.BankTransactionRepository;
import com.ramzi.backend.service.ClassificationNotificationService;
import com.ramzi.backend.service.TransactionFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionFeedbackService feedbackService;
    private final ClassificationNotificationService notificationService;
    private final BankTransactionRepository transactionRepository;

    @PostMapping("/api/transactions/{id}/feedback")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackRequest request,
            Principal principal) {
        return ResponseEntity.ok(feedbackService.applyFeedback(principal.getName(), id, request));
    }

    @GetMapping("/api/transactions/pending-review")
    public ResponseEntity<List<PendingReviewDto>> pendingReview(Principal principal) {
        List<PendingReviewDto> pending = transactionRepository
                .findByBankAccount_User_EmailAndClassificationStatus(
                        principal.getName(), ClassificationStatus.PENDING_REVIEW)
                .stream()
                .map(tx -> new PendingReviewDto(
                        tx.getId(),
                        tx.getAmount(),
                        tx.getPurpose(),
                        tx.getCounterpartyName(),
                        tx.getCounterpartyIban(),
                        tx.getClassification(),
                        tx.getConfidenceScore(),
                        tx.isSuggestNewContract()
                ))
                .toList();
        return ResponseEntity.ok(pending);
    }

    @GetMapping("/api/notifications/classification-reviews")
    public ResponseEntity<List<ClassificationNotificationDto>> reviewNotifications(Principal principal) {
        return ResponseEntity.ok(notificationService.findByEmail(principal.getName()));
    }
}
