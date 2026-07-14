package com.odontorisas.infra.tenancy;

import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Establece el contexto de tenant <b>en toda transacción</b>, automáticamente.
 *
 * <p>Este es el punto que hace que el aislamiento sea <b>por defecto, no por disciplina</b>:
 * una capacidad de negocio nueva declara su entidad siguiendo la convención
 * ({@code docs/backend.md §8.1}) y queda aislada <b>sin escribir una línea de código de
 * aislamiento</b>. No hay nada que recordar — y por tanto nada que olvidar.
 *
 * <p><b>Por qué aquí y no en un aspecto sobre {@code @Transactional}:</b> un aspecto depende del
 * orden de los advisors, y un orden mal configurado <em>no rompe la build: rompe el aislamiento
 * en silencio</em>. El gestor de transacciones es el único punto por el que pasan <b>todas</b>
 * las transacciones, sin excepción y sin depender de una precedencia sutil.
 *
 * <p><b>Por qué {@code super.doBegin(...)} va primero:</b> {@link JpaTransactionManager#doBegin}
 * enlaza el {@link EntityManagerHolder} al hilo <em>al final</em>. Antes de eso, el
 * {@code EntityManager} de la transacción no es alcanzable, y un {@code @PersistenceContext}
 * abriría <em>otro</em> sobre <em>otra</em> conexión. Como {@code set_config(..., true)} es
 * transaction-local, la variable acabaría en la conexión equivocada y se descartaría con ella:
 * la transacción real nunca la vería. No sería una fuga (RLS falla cerrado), pero el código
 * <em>parecería</em> correcto. De ahí el orden: primero abrir, después fijar.
 *
 * <p><b>Sin contexto no se fija nada</b> (actuator, tareas de sistema, y los flujos de auth
 * previos a la sesión). Es deliberado: si esa transacción toca una tabla de negocio, RLS
 * <b>falla cerrado</b> — que es exactamente lo que debe pasar. Un fallo ruidoso se arregla en
 * cinco minutos; un resultado vacío se confunde con "esta clínica no tiene datos" y puede
 * tardar meses en detectarse.
 *
 * <p>Lee el contexto de {@link IdentityContext} y {@link OrganizationContext}, los
 * {@code ThreadLocal} que el filtro JWT puebla desde el token <b>verificado</b> (nunca desde
 * datos del cliente) y limpia al terminar la petición. Depende, por tanto, de que la petición
 * se atienda en <b>un solo hilo</b>: por eso el tripwire de ArchUnit contra {@code @Async} y
 * {@code @Scheduled} no es un detalle suelto, <b>sostiene este mecanismo</b>.
 */
@Component
public class TenantAwareTransactionManager extends JpaTransactionManager {

    public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        Long userId = IdentityContext.get();
        if (userId == null) {
            return; // Sin identidad no hay contexto que propagar. RLS hará su trabajo.
        }
        EntityManager entityManager = transactionalEntityManager();
        if (entityManager == null) {
            return;
        }
        Long organizationId = OrganizationContext.get();
        if (organizationId != null) {
            TenantSession.bind(entityManager, userId, organizationId);
        } else {
            TenantSession.bindIdentity(entityManager, userId);
        }
    }

    /** El {@code EntityManager} que {@code super.doBegin} acaba de enlazar a esta transacción. */
    private EntityManager transactionalEntityManager() {
        Object resource = TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        return resource instanceof EntityManagerHolder holder ? holder.getEntityManager() : null;
    }
}
