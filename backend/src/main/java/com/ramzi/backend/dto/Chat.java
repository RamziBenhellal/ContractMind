package com.ramzi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
public class Chat {

    @Setter @Getter
    public static class ChatQuestionRequest{
        @NotBlank String question;
    }


    @Setter @Getter
    public static class ChatAnswerResponse{
        public ChatAnswerResponse(String answer){
            this.answer = answer;
        }
        @NotBlank
        private  String answer;
    }
}
