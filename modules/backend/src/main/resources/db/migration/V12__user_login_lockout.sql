-- Capacidad: update-authentication (bloqueo por intentos fallidos de login).
--
-- failed_login_attempts / locked_until son el bloqueo AUTOMÁTICO y TEMPORAL por fuerza bruta,
-- independiente de UserStatus.LOCKED/DISABLED (estado administrativo, permanente). No se toca
-- ese enum aquí a propósito (design.md D12): si el lockout automático lo reutilizara, expirar
-- la ventana obligaría a devolver el status a ACTIVE, resucitando una cuenta que un admin
-- bloqueó a mano.
--
-- Aditivas con default, sin backfill: los usuarios existentes arrancan con 0 intentos y sin
-- bloqueo, que es exactamente su estado real hoy (nadie ha fallado un login que no exista).

ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ NULL;
