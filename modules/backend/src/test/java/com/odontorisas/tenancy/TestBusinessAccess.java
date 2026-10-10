package com.odontorisas.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Una "capacidad de negocio" de mentira: accede a la tabla-fixture {@code test_business} —que
 * lleva la política {@code organization_isolation}, la misma convención que seguirá cada tabla
 * real— y <b>no contiene una sola línea de código de aislamiento</b>.
 *
 * <p>Eso es justo lo que se quiere demostrar: <b>la ausencia de {@code TenantSession.bind(...)}
 * aquí es el aserto</b>. Si esta clase tuviera que fijar el contexto, el requirement
 * <em>"una entidad de negocio nueva queda aislada sin código propio"</em> sería falso.
 *
 * <p>Usa <b>consulta nativa</b> a propósito: así se salta el filtro de aplicación
 * ({@code @TenantId}) y el aislamiento que se observe solo puede venir de <b>Row-Level Security</b>
 * — que a su vez solo funciona si <em>alguien</em> fijó {@code app.organization_id}. Ese alguien
 * es el enganche, y nadie más.
 */
@Transactional
public class TestBusinessAccess {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<String> visibleLabels() {
        return entityManager.createNativeQuery("SELECT label FROM test_business").getResultList();
    }
}
