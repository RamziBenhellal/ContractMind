package com.ramzi.backend.repository;

import com.ramzi.backend.entity.ClassificationMatchType;
import com.ramzi.backend.entity.ClassificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassificationRuleRepository extends JpaRepository<ClassificationRule, Long> {

    List<ClassificationRule> findByUser_Id(Long userId);

    List<ClassificationRule> findByUser_Email(String email);

    List<ClassificationRule> findByUser_IdAndMatchType(Long userId, ClassificationMatchType matchType);
}
