package com.devfolio.controller;

import com.devfolio.model.User;
import com.devfolio.repository.UserRepository;
import com.devfolio.service.AuthService;
import com.devfolio.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("email");
        String password = request.get("password");

        Optional<User> userOpt = userRepository.findByEmail(username);

        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Identifiants invalides"));
        }

        User user = userOpt.get();

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(Map.of("token", token, "user", user));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || email.isBlank() || password == null || password.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email invalide ou mot de passe de 6 caractères minimum requis"));
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mail ou mot de passe invalide"));
        }

        User user = authService.register(email, password);
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(Map.of("token", token, "user", user));
    }

    @PostMapping("/reset-password/request")
    public ResponseEntity<?> requestReset(@RequestParam String email) {
        return ResponseEntity.ok(Map.of("message", "Si l'adresse existe, un lien a été envoyé par email."));
    }
}
