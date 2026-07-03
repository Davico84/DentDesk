package com.odontorisas.presentation.controller;

import com.odontorisas.infra.security.RefreshCookieFactory;
import com.odontorisas.presentation.dto.AuthResponse;
import com.odontorisas.presentation.dto.LoginRequest;
import com.odontorisas.presentation.dto.RegisterRequest;
import com.odontorisas.service.auth.AuthResult;
import com.odontorisas.service.auth.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticación. El access token va en el cuerpo; el refresh token
 * se emite/borra como cookie HttpOnly ({@link RefreshCookieFactory}).
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "auth", description = "Autenticación")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    public AuthController(AuthService authService, RefreshCookieFactory refreshCookieFactory) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authResponse(authService.register(request.email(), request.password()), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authResponse(authService.login(request.email(), request.password()), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${app.security.cookie.refresh-name}", required = false) String refreshToken) {
        return authResponse(authService.refresh(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.security.cookie.refresh-name}", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
            .build();
    }

    private ResponseEntity<AuthResponse> authResponse(AuthResult result, HttpStatus status) {
        ResponseCookie cookie = refreshCookieFactory.create(result.refreshToken(), result.refreshExpiresAt());
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(AuthResponse.from(result));
    }
}
