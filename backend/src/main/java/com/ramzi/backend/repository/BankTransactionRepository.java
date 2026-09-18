package com.ramzi.backend.repository;

import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.TransactionClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    boolean existsByBankAccountAndExternalId(BankAccount bankAccount, String externalId);

    List<BankTransaction> findByBankAccount_Id(Long bankAccountId);

    List<BankTransaction> findByBankAccount_User_Id(Long userId);

    List<BankTransaction> findByClassification(TransactionClassification classification);
}
