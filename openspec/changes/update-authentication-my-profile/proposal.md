# Proposal

## Why

Un usuario no tiene dónde ver ni corregir sus propios datos: el nombre se carga al registrarse y después no se puede
cambiar, y no hay forma de cambiar la contraseña. El nombre importa más desde `update-staff-professional-credential`,
porque es el que se copia en la firma de los actos clínicos. Y si alguien sospecha que otro conoce su contraseña, hoy
no puede cambiarla ni cerrar las sesiones que el otro tenga abiertas.

`docs/vision.md` ya asigna esta pieza a `authentication`: *editar mi perfil es dato de la identidad, no una capacidad
nueva* (§2, "Por qué ya no existe una capacidad `users`").

## What Changes

- **Pantalla "Mi perfil"** en el menú de la cuenta, en reemplazo de la entrada "Mi credencial profesional". Reúne:
  - **datos personales**: nombre editable y correo **solo de lectura**;
  - **contraseña**: cambiarla exigiendo la actual;
  - **credencial profesional**: la página que hoy vive suelta en `/profile/credential` pasa a ser una sección de
    Mi perfil, sin cambiar su comportamiento.
- **Corregir el nombre propio**: con el rango del registro (2–120 caracteres, sin contar los espacios de los extremos). Lo ya firmado no cambia:
  la firma guarda una copia del nombre.
- **Cambiar la contraseña** con la actual: la nueva sigue la regla del registro (mínimo 8 caracteres) y debe ser
  distinta de la actual. Una contraseña actual incorrecta **cuenta como intento fallido** para el bloqueo temporal
  del login, para que una sesión robada no sirva para adivinarla.
- **Al cambiar la contraseña se cierran las otras sesiones** del usuario; la sesión desde la que se cambió sigue
  abierta.
- **Ayuda del campo "Habilitado hasta"** de la credencial: dice que es la **fecha de vigencia de la constancia de
  habilitación del COP**. El responsable confirmó que la constancia la trae impresa; cierra la Open Question del
  design de `update-staff-professional-credential`.

## Non-goals

- **Recuperar la contraseña olvidada** (el enlace "¿Olvidaste tu contraseña?" del login, hoy sin backend): change
  propio, **inmediatamente después** de este. Su actor es alguien sin sesión, con token por correo y anti-enumeración.
- **Cambiar el correo**: es la identidad de login; cambiarlo exige verificar la dirección nueva, otro flujo.
- **Teléfono, país u otros datos** del usuario: no existen desde `V5` (registro mínimo); no se reintroducen.
- **Listar o cerrar sesiones una por una** (dispositivos): solo se cierran todas las demás al cambiar la contraseña.
- **Reglas de contraseña más fuertes** que las del registro: se mantienen las mismas.
- **Perfil de la sesión de plataforma**: el administrador de plataforma no usa esta pantalla.
- Cambios a la credencial profesional más allá de su ubicación y su texto de ayuda.

## Capabilities

### New Capabilities

_Ninguna._

### Modified Capabilities

- `authentication`: se agregan ver y corregir el perfil propio (nombre; correo solo de lectura) y cambiar la propia
  contraseña con la actual, cerrando las otras sesiones.

La credencial profesional (`staff`) solo cambia de lugar en la interfaz y de texto de ayuda: sus requirements no
cambian.

## Impact

- **Backend**: endpoints del perfil propio y del cambio de contraseña (este último bajo `/auth`, donde viaja la cookie
  de refresco de la sesión actual); revocación de los tokens de refresco del usuario; reutiliza el conteo de intentos
  fallidos de `update-authentication`. Sin migraciones: `users` ya tiene `full_name` y `password_hash`.
- **Contrato**: `contracts/openapi.json` y el cliente orval regenerados.
- **Frontend**: pantalla y ruta de Mi perfil, menú de la cuenta, la credencial como sección, enlaces de
  `SigningCapacityNotice` a la nueva ubicación, y la sesión en memoria actualizada al cambiar el nombre.
- **Docs**: `docs/vision.md` (estado), `docs/domain.md` solo si cambia el modelo (no se espera).
