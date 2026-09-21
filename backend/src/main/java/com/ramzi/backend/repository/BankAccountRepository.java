package com.ramzi.backend.repository;

import com.ramzi.backend.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByUser_Email(String email);

    List<BankAccount> findByUser_Id(Long userId);

    Optional<BankAccount> findByUser_IdAndIban(Long userId, String iban);

    Optional<BankAccount> findByIdAndUser_Email(Long id, String email);
}
