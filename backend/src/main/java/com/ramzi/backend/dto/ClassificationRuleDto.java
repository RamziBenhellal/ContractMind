package com.ramzi.backend.dto;

import com.ramzi.backend.entity.ClassificationMatchType;
import com.ramzi.backend.entity.ClassificationTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationRuleDto {
    private Long id;
    private Long userId;
    private ClassificationMatchType matchType;
    private String matchValue;
    private ClassificationTargetType targetType;
    private Long targetEntityId;
    private Long learnedFromTransactionId;
    private Instant createdAt;
}
