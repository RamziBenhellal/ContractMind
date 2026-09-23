package com.ramzi.backend.repository;

import com.ramzi.backend.entity.BankConnection;
import com.ramzi.backend.entity.BankConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {

    Optional<BankConnection> findByIdAndUser_Email(UUID id, String email);

    List<BankConnection> findByStatus(BankConnectionStatus status);

    List<BankConnection> findByUser_IdAndStatus(Long userId, BankConnectionStatus status);

    Optional<BankConnection> findByPythonSessionId(String pythonSessionId);
}
