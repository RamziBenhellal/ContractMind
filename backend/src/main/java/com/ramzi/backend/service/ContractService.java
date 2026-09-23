package com.ramzi.backend.service;

import com.ramzi.backend.dto.ContractDto;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.repository.ContractRepository;
import com.ramzi.backend.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final AiIntegrationService aiIntegrationService;
    private final BankCalendarService calendarService;

    public List<ContractDto> getAllContractsForUser(String email) {
        List<Contract> contracts = contractRepository.findByUser_Email(email);
        return contracts.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private ContractDto mapToDto(Contract contract) {
        return ContractDto.builder()
                .id(contract.getId())
                .provider(contract.getProvider())
                .contractType(contract.getContractType())
                .monthlyCost(contract.getMonthlyCost())
                .endDate(contract.getEndDate())
                .status(contract.getStatus())
                .dueDayOfMonth(contract.getDueDayOfMonth())
                .build();
    }

    public ContractDto addContractAi(String email, String filePath) {
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Nutzer nicht gefunden"));

        Contract pendingContract = Contract.builder()
                .filePath(filePath)
                .status("WAITING_FOR_AI")
                .provider("Wird analysiert...")
                .contractType("Unbekannt ")
                .monthlyCost(BigDecimal.ZERO)
                .user(currentUser)
                .build();

        Contract savedContract = contractRepository.save(pendingContract);

        aiIntegrationService.analyzeAndProcessContract(savedContract.getId(), filePath);
        System.out.println(pendingContract.getStatus());

        return mapToDto(pendingContract);
    }

    public ContractDto addContractManually(String email, ContractDto contractDto) {
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Nutzer nicht gefunden"));

        Contract newContract = new Contract();
        newContract.setProvider(contractDto.getProvider());
        newContract.setContractType(contractDto.getContractType());
        newContract.setMonthlyCost(contractDto.getMonthlyCost());
        newContract.setEndDate(contractDto.getEndDate());
        newContract.setDueDayOfMonth(contractDto.getDueDayOfMonth());
        newContract.setStatus("VERIFIED");
        newContract.setUser(currentUser);

        Contract contract = contractRepository.save(newContract);
        calendarService.ensureContractEntry(currentUser, contract, LocalDate.now());
        return mapToDto(contract);
    }

    public ContractDto editContract(Long id, ContractDto updatedContract) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        contract.setProvider(updatedContract.getProvider());
        contract.setContractType(updatedContract.getContractType());
        contract.setMonthlyCost(updatedContract.getMonthlyCost());
        contract.setEndDate(updatedContract.getEndDate());
        contract.setDueDayOfMonth(updatedContract.getDueDayOfMonth());
        contract.setStatus("VERIFIED");

        Contract savedContract = contractRepository.save(contract);
        calendarService.ensureContractEntry(savedContract.getUser(), savedContract, LocalDate.now());
        return mapToDto(savedContract);
    }
}
