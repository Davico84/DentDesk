package com.odontorisas.infra.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee el access token de la cabecera {@code Authorization: Bearer <token>}, lo valida y, si es
 * correcto, autentica la petición con el rol del usuario y establece el contexto de tenant
 * ({@link IdentityContext} y {@link OrganizationContext}) a partir de los claims firmados —
 * nunca de datos de la petición. Un token ausente, inválido o sin organización activa deja la
 * petición sin autenticar (el entry point responde 401 si la ruta lo requiere).
 *
 * <p>El contexto y el MDC se limpian SIEMPRE en el {@code finally}: un {@link ThreadLocal} sucio
 * en el pool de hilos haría que la siguiente petición heredara el tenant de la anterior.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    static final String MDC_USER_ID = "userId";
    static final String MDC_ORGANIZATION_ID = "organizationId";

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                AccessClaims claims = tokenService.verifyAccessToken(token);
                IdentityContext.set(claims.userId());
                OrganizationContext.set(claims.organizationId());
                MDC.put(MDC_USER_ID, String.valueOf(claims.userId()));
                MDC.put(MDC_ORGANIZATION_ID, String.valueOf(claims.organizationId()));
                var authentication = new UsernamePasswordAuthenticationToken(
                    claims.email(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
                authentication.setDetails(claims.userId());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JWTVerificationException ex) {
                // Token inválido/expirado/sin organización: seguimos sin autenticar.
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            IdentityContext.clear();
            OrganizationContext.clear();
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_ORGANIZATION_ID);
        }
    }
}
