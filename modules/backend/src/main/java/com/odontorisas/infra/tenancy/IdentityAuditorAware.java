package com.odontorisas.infra.tenancy;

import com.odontorisas.common.IdentityContext;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Expone {@link IdentityContext} a Spring Data JPA Auditing: es el adaptador fino que hace que
 * {@code @CreatedBy}/{@code @LastModifiedBy} se rellenen solos, con el mismo contexto por
 * transacción que ya puebla {@link TenantAwareTransactionManager} — no hay plomería nueva, solo
 * conectar algo que ya existe a un mecanismo distinto.
 *
 * <p>{@code Optional.empty()} cuando no hay identidad en el contexto (los flujos previos a la
 * sesión, como el alta de la primera organización) es <b>el caso esperado</b>, no un error:
 * Spring Data Auditing lo tolera dejando la autoría sin asignar, igual que
 * {@code TenantAwareTransactionManager#doBegin} no fija nada cuando no hay identidad que propagar.
 */
@Component
public class IdentityAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        return Optional.ofNullable(IdentityContext.get());
    }
}
