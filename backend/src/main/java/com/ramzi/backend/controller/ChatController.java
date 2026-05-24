package com.ramzi.backend.controller;

import com.ramzi.backend.dto.Chat.ChatQuestionRequest;
import com.ramzi.backend.dto.Chat.ChatAnswerResponse;
import com.ramzi.backend.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:4200")
public class ChatController {

    @Autowired
    private ChatService chatService;


    @PostMapping("/ask")
    public ResponseEntity<ChatAnswerResponse> askQuestion(@RequestBody ChatQuestionRequest chatQuestionRequest, Principal principal) {

        String answer = chatService.askAiAssistant(chatQuestionRequest.getQuestion(), principal.getName());
        return ResponseEntity.ok(new ChatAnswerResponse(answer));
    }
}
