package com.ramzi.backend.controller;

import com.ramzi.backend.dto.ContractDto;
import com.ramzi.backend.repository.ContractRepository;
import com.ramzi.backend.service.ContractService;
import com.ramzi.backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;
    private final FileStorageService fileStorageService;
    private final ContractRepository contractRepository;

    @GetMapping
    public ResponseEntity<List<ContractDto>> getMyContracts(Principal principal) {
        String email = principal.getName();
        List<ContractDto> myContracts = contractService.getAllContractsForUser(email);
        return ResponseEntity.ok(myContracts);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractDto> uploadContractFile(
            @RequestParam("file") MultipartFile file,
            Principal principal) {

        // 1. Datei speichern
        String savedFilePath = fileStorageService.storeFile(file);
        ContractDto contractDto = contractService.addContractAi(principal.getName(), savedFilePath);
        return ResponseEntity.ok(contractDto);
    }

    @PostMapping("/add")
    ResponseEntity<ContractDto> addContract(@RequestBody ContractDto contractDto, Principal principal) {
        ContractDto newContract = contractService.addContractManually(principal.getName(),contractDto);
        return ResponseEntity.ok(newContract);

    }

    @PutMapping("/{id}")
    public ResponseEntity<ContractDto> updateContract(
            @PathVariable Long id,
            @RequestBody ContractDto updatedData
    ){
        ContractDto contractDto = contractService.editContract(id, updatedData);
        return ResponseEntity.ok(contractDto);
    }

    @DeleteMapping("{id}")
    ResponseEntity<Void> deleteContract(@PathVariable Long id) {
        contractRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
