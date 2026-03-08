package com.testehan.finana.controller;

import com.testehan.finana.model.user.User;
import com.testehan.finana.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserRepository userRepository;
    private final JwtDecoder jwtDecoder;

    public UserController(UserRepository userRepository, JwtDecoder jwtDecoder) {
        this.userRepository = userRepository;
        this.jwtDecoder = jwtDecoder;
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncUser(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String email = request.get("email");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }

        String name = null;
        String picture = null;
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Jwt jwt = jwtDecoder.decode(token);
                String tokenEmail = jwt.getClaimAsString("email");
                if (tokenEmail != null && !tokenEmail.equals(email)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Email mismatch"));
                }
                name = jwt.getClaimAsString("name");
                picture = jwt.getClaimAsString("picture");
            } catch (Exception e) {
                logger.debug("Invalid JWT token: {}", e.getMessage());
            }
        }

        var existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "email", user.getEmail(),
                "userId", user.getId(),
                "createdAt", user.getCreatedAt().toString()
            ));
        }

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setName(name);
        newUser.setPicture(picture);
        newUser.setCreatedAt(Instant.now());
        User savedUser = userRepository.save(newUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "email", savedUser.getEmail(),
            "userId", savedUser.getId(),
            "createdAt", savedUser.getCreatedAt().toString()
        ));
    }
}