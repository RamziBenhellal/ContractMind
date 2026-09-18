package com.ramzi.backend.repository;

import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.ClassificationStatus;
import com.ramzi.backend.entity.TransactionClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    boolean existsByBankAccountAndExternalId(BankAccount bankAccount, String externalId);

    List<BankTransaction> findByBankAccount_Id(Long bankAccountId);

    List<BankTransaction> findByBankAccount_User_Id(Long userId);

    List<BankTransaction> findByClassification(TransactionClassification classification);

    List<BankTransaction> findByBankAccount_User_EmailAndClassificationStatus(
            String email, ClassificationStatus status);

    Optional<BankTransaction> findByIdAndBankAccount_User_Email(Long id, String email);
}
