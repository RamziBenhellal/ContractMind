package com.ramzi.backend.service;

import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.repository.ContractRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    private ContractRepository contractRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String PYTHON_CHAT_URL = "http://localhost:8000/api/chat/ask";

    public String askAiAssistant(String question, String email) {
        List<Contract> userContracts = contractRepository.findContractByUserEmailAndStatus(email, "VERIFIED");
        String context;
        if (userContracts.isEmpty()) {
            context = "Der Nutzer hat aktuell keine gespeicherten Verträge.";
        } else {
            context = userContracts.stream()
                    .map(c -> String.format("- %s (%s): %s€ monatlich, Kündigungsfrist: %s",
                            c.getProvider(), c.getContractType(), c.getMonthlyCost(), c.getEndDate()))
                    .collect(Collectors.joining("\n"));
        }

        // Den Request für den Python-Server zusammenbauen
        Map<String,String> pythonRequest = new HashMap<>();
        pythonRequest.put("question", question);
        pythonRequest.put("context", context);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String,String>> requestEntity = new HttpEntity<>(pythonRequest, headers);

        try{
            ResponseEntity<Map> response = restTemplate.postForEntity(PYTHON_CHAT_URL, requestEntity, Map.class);
            if(response.getBody() != null && response.getBody().containsKey("answer")){
                return (String) response.getBody().get("answer");
            }
        }
        catch(Exception e){
            System.err.println("Failed to communicate with AI Assistant"+e.getMessage());
            return "Entschuldigung, meine KI-Server sind gerade nicht erreichbar..";

        }

        return "Ich konnte leider keine Antwort generieren.";
    }


}
