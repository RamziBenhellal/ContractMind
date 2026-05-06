package com.ramzi.backend.controller;

import com.ramzi.backend.dto.AuthDto.*;
import com.ramzi.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            authService.Register(request.email(), request.password());
            return ResponseEntity.ok(new AuthResponse(null, request.email(), "Registered Successfully"));
        }
        catch (RuntimeException e){
            return ResponseEntity.badRequest().body(new AuthResponse(null, null, e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request){
        try {
            String token = authService.Login(request.email(), request.password());
            return ResponseEntity.ok(new AuthResponse(token, request.email(),"Login Successfully"));
        }
        catch (RuntimeException e){
            return ResponseEntity.status(401).body(new AuthResponse(null, null, e.getMessage()));
        }
    }


}
