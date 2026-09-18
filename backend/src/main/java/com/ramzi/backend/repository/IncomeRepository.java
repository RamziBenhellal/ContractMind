package com.ramzi.backend.repository;

import com.ramzi.backend.entity.Income;
import com.ramzi.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncomeRepository extends JpaRepository<Income, Long> {
    List<Income> findByUser_Email(String email);
    List<Income> findByUser_Id(Long userId);
}
