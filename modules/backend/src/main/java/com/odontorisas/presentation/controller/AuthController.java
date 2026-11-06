package com.odontorisas.presentation.controller;

import com.odontorisas.infra.security.ClientIpDiagnostics;
import com.odontorisas.infra.security.ClientIpResolver;
import com.odontorisas.infra.security.RefreshCookieFactory;
import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.AuthResponse;
import com.odontorisas.presentation.dto.ChangePasswordRequest;
import com.odontorisas.presentation.dto.LoginRequest;
import com.odontorisas.presentation.dto.InvitationValidationResponse;
import com.odontorisas.presentation.dto.InvitationValidationRequest;
import com.odontorisas.presentation.dto.InvitationVerificationRequest;
import com.odontorisas.presentation.dto.PasswordRecoveryRequest;
import com.odontorisas.presentation.dto.PasswordResetCheckRequest;
import com.odontorisas.presentation.dto.PasswordResetRequest;
import com.odontorisas.presentation.dto.PasswordResetResponse;
import com.odontorisas.presentation.dto.RegisterRequest;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.auth.AuthResult;
import com.odontorisas.service.auth.AuthService;
import com.odontorisas.service.auth.PasswordRecoveryService;
import com.odontorisas.service.auth.PasswordResetService;
import com.odontorisas.service.auth.RegisterCommand;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /** Los errores viajan en RFC 9457 (ver {@code ProblemDetails}). */
    private static final String PROBLEM_JSON = "application/problem+json";

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final com.odontorisas.service.staff.StaffService staffService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;
    private final ClientIpDiagnostics clientIpDiagnostics;

    public AuthController(AuthService authService, RefreshCookieFactory refreshCookieFactory,
                          com.odontorisas.service.staff.StaffService staffService,
                          PasswordRecoveryService passwordRecoveryService,
                          PasswordResetService passwordResetService,
                          ClientIpResolver clientIpResolver,
                          ClientIpDiagnostics clientIpDiagnostics) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.staffService = staffService;
        this.passwordRecoveryService = passwordRecoveryService;
        this.passwordResetService = passwordResetService;
        this.clientIpResolver = clientIpResolver;
        this.clientIpDiagnostics = clientIpDiagnostics;
    }

    /**
     * Devuelve <b>201</b>, no 200: se crea una organización con su administrador. Hay que declararlo
     * porque el status se decide en runtime ({@link ResponseEntity}) y la herramienta no lo infiere.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Organización y administrador creados; sesión iniciada.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Campos inválidos (ver `errors` para el detalle por campo).",
            content = @Content(mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "409",
            description = "El correo ya está registrado, o ya existe una organización con ese identificador. "
                + "El campo `type` discrimina cuál de los dos (`/errors/email-already-exists` o "
                + "`/errors/organization-slug-taken`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterCommand command = new RegisterCommand(
            request.token(), request.email(), request.password(), request.fullName());
        return authResponse(authService.register(command), HttpStatus.CREATED);
    }

    @PostMapping("/invitations/validate")
    public InvitationValidationResponse validateInvitation(@jakarta.validation.Valid @RequestBody InvitationValidationRequest request) {
        return InvitationValidationResponse.from(staffService.validate(request.token()));
    }

    @PostMapping("/invitations/verify")
    public ResponseEntity<Void> verifyInvitation(@Valid @RequestBody InvitationVerificationRequest request) {
        staffService.verifyEmail(request.token(), request.email(), request.code());
        return ResponseEntity.noContent().build();
    }

    /**
     * El {@code 200} se declara explícitamente: en cuanto una operación declara <b>cualquier</b>
     * {@code @ApiResponse}, la herramienta <b>descarta la respuesta de éxito que infería</b>. Sin
     * esta línea el contrato perdería el schema de {@link AuthResponse} y el cliente generado se
     * quedaría sin el tipo de la respuesta exitosa.
     *
     * <p>El {@code 401} es <b>semántico</b> —credenciales inválidas—, no el "sesión ausente" que
     * inyecta el customizer: esta ruta es pública, así que hay que declararlo aquí.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sesión iniciada.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Campos inválidos.",
            content = @Content(mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authResponse(authService.login(request.email(), request.password()), HttpStatus.OK);
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sesión renovada; se rota la cookie de refresco.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Falta la cookie de refresco, o es inválida o está revocada.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${app.security.cookie.refresh-name}", required = false) String refreshToken) {
        return authResponse(authService.refresh(refreshToken), HttpStatus.OK);
    }

    /**
     * Cambia la contraseña propia y cierra las otras sesiones; la actual sigue con un access y una cookie
     * nuevos ({@code update-authentication-my-profile} design.md D1/D2). Bajo {@code /auth} porque es una
     * operación de sesión, como {@code refresh} y {@code logout}. Es la única ruta de {@code /auth} que exige
     * sesión: no está en {@code PublicPaths}, así que el customizer le agrega el 401 de sesión ausente; una
     * sesión de plataforma llega sin autenticar.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "Contraseña cambiada; las otras sesiones se cerraron y se rota la cookie de refresco.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400",
            description = "Campos inválidos, contraseña actual incorrecta (`/errors/current-password-incorrect`; "
                + "cuenta como intento fallido) o nueva igual a la actual (`/errors/new-password-same-as-current`).",
            content = @Content(mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "429",
            description = "Cuenta bloqueada temporalmente por intentos fallidos (`/errors/account-temporarily-locked`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
    @PostMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authResponse(authService.changePassword(request.currentPassword(), request.newPassword()),
            HttpStatus.OK);
    }

    /**
     * Pedido de recuperación ({@code update-authentication-password-recovery} design.md D1, D3, D10). Responde
     * <b>siempre</b> {@code 202} sin cuerpo, exista o no la cuenta, cualquiera sea su estado, su límite, el de
     * la IP o el de la cola: la petición no busca la cuenta. Solo un formato inválido da {@code 400}, que
     * depende de lo enviado y no de la cuenta.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "202",
            description = "Pedido recibido. Si existe una cuenta activa con ese correo, se le envía un enlace."),
        @ApiResponse(responseCode = "400", description = "Correo vacío o con formato inválido.",
            content = @Content(mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ValidationProblem.class)))
    })
    @PostMapping("/password-recovery")
    public ResponseEntity<Void> requestPasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request,
                                                        HttpServletRequest http) {
        String clientIp = clientIpResolver.resolve(http);
        clientIpDiagnostics.record(http, clientIp);
        passwordRecoveryService.request(request.email(), clientIp);
        return ResponseEntity.accepted().build();
    }

    /** ¿El enlace sigue vigente? El mismo {@code 400} para inválido, vencido, usado o anulado (design.md D1). */
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "El enlace es vigente."),
        @ApiResponse(responseCode = "400",
            description = "El enlace no es válido o ya venció (`/errors/invalid-reset-link`), sin decir por qué.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PostMapping("/password-reset/check")
    public ResponseEntity<Void> checkPasswordReset(@Valid @RequestBody PasswordResetCheckRequest request) {
        passwordResetService.check(request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * Restablece la contraseña con el enlace (design.md D1, D5): cierra todas las sesiones y <b>no</b> abre una
     * nueva (no hay {@code Set-Cookie}). {@code outcome} dice si ya puede entrar.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña cambiada; todas las sesiones se cerraron.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = PasswordResetResponse.class))),
        @ApiResponse(responseCode = "400",
            description = "Campos inválidos (no consume el enlace), o el enlace no es válido o ya venció "
                + "(`/errors/invalid-reset-link`).",
            content = @Content(mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ValidationProblem.class)))
    })
    @PostMapping("/password-reset")
    public PasswordResetResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return PasswordResetResponse.from(passwordResetService.reset(request.token(), request.newPassword()));
    }

    /** Devuelve <b>204</b> (sin cuerpo), no 200. Hay que declararlo por la misma razón que el 201. */
    @ApiResponse(responseCode = "204", description = "Sesión cerrada; la cookie de refresco se invalida.")
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
