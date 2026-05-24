package com.ramzi.backend.repository;

import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    List<Contract> findByUser_Email(String email);
    List<Contract> findContractByUserEmailAndStatus(String email, String status);

}
