package com.ramzi.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDto {

    public record RegisterRequest(
            @Email(message = "Ungültige E-Mail-Adresse")
            @NotBlank(message = "E-Mail darf nicht leer sein")
            String email,
            @NotBlank(message = "Passwort darf nicht leer sein")
            String password
            )
    {}
    public record LoginRequest(@NotBlank(message = "Benutzername darf nicht leer sein")
                               String email,
                               @NotBlank(message = "Passwort darf nicht leer sein")
                               String password
    )
    {}

    public record AuthResponse(String token, String email, String message){}
}
