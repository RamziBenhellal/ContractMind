package com.ramzi.backend.service;

import com.ramzi.backend.dto.ClassificationNotificationDto;
import com.ramzi.backend.event.ClassificationReviewNeededEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClassificationNotificationService {

    private final Map<String, List<ClassificationNotificationDto>> byEmail = new ConcurrentHashMap<>();

    @EventListener
    public void onReviewNeeded(ClassificationReviewNeededEvent event) {
        if (event.userEmail() == null) {
            return;
        }
        ClassificationNotificationDto notification = new ClassificationNotificationDto(
                event.transactionId(),
                event.suggestNewContract(),
                event.title(),
                event.amount(),
                Instant.now()
        );
        byEmail.compute(event.userEmail(), (email, existing) -> {
            List<ClassificationNotificationDto> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            next.removeIf(item -> item.transactionId().equals(event.transactionId()));
            next.add(notification);
            return next;
        });
    }

    public List<ClassificationNotificationDto> findByEmail(String email) {
        return List.copyOf(byEmail.getOrDefault(email, List.of()));
    }

    public void dismiss(String email, Long transactionId) {
        byEmail.computeIfPresent(email, (key, existing) -> {
            List<ClassificationNotificationDto> next = existing.stream()
                    .filter(item -> !item.transactionId().equals(transactionId))
                    .toList();
            return next.isEmpty() ? null : new ArrayList<>(next);
        });
    }
}
