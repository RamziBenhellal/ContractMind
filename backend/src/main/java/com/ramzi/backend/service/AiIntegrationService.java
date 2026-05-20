package com.ramzi.backend.service;

import com.ramzi.backend.dto.AiResponseDto;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AiIntegrationService {

    @Autowired
    private ContractRepository contractRepository;
    private RestTemplate restTemplate = new RestTemplate();

    @Async
    public void analyzeAndProcessContract(Long contractId, String filrPath) {
        try {
            System.out.println("🤖 Starte KI-Analyse für Vertrag ID: " + contractId);

            // 1. load file
            File file = new File(filrPath);
            if(!file.exists()) throw new RuntimeException("File not found: " + filrPath);
            FileSystemResource resource = new FileSystemResource(file);

            // 2. HTTP Request (Multipart Form Data) an Python zusammenbauen
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 3. Send Request to Python
            ResponseEntity<AiResponseDto> response =
                    restTemplate.postForEntity("http://localhost:8000/api/contracts/analyze", requestEntity, AiResponseDto.class);

            if(response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AiResponseDto.AiContractData aiData = response.getBody().getData();
                System.out.println(response.getBody().getData());

                Contract contract = contractRepository.findById(contractId).orElseThrow();

                if (aiData != null && aiData.isContract() ) {
                    contract.setProvider(aiData.getProvider());
                    contract.setContractType(aiData.getContractType());
                    contract.setMonthlyCost(aiData.getMonthlyCost());

                    try {
                        contract.setEndDate(LocalDate.parse(aiData.getEndDate()));
                    } catch (Exception e) {
                        contract.setEndDate(null);
                    }


                    contract.setStatus("NEEDS_REVIEW");
                    System.out.println("✅ KI-Analyse erfolgreich! Daten gespeichert.");
                } else {
                    contract.setStatus("ERROR_NO_CONTRACT");
                    contract.setProvider("No Contract Found");
                }

                contractRepository.save(contract);
            }
        }
        catch(Exception e) {
            System.err.println("❌ Fehler bei der KI-Analyse: " + e.getMessage());
            contractRepository.findById(contractId).ifPresent(contract -> {
                contract.setStatus("AI_ERROR");
                contractRepository.save(contract);
            });
        }
    }
}
