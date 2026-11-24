## Purpose

Agenda interna de la clínica: el personal agenda, reprograma y cierra las citas de sus pacientes con un odontólogo
habilitado, dentro del horario de atención y sin choques en la agenda de cada odontólogo, y desde la cita abre la
nota de consulta. El paciente no usa el sistema; la reserva online es `public-booking`.

## ADDED Requirements

### Requirement: Agenda de la organización activa para todo su personal

El sistema SHALL permitir consultar y gestionar citas a cualquier miembro activo (`ADMIN` o `MEMBER`) de la
organización activa, y SHALL limitar toda lectura y escritura a las citas de esa organización. Una cita de otra
organización SHALL responder como inexistente. Una sesión sin organización activa o de plataforma SHALL ser
rechazada.

#### Scenario: Un miembro consulta la agenda
- **WHEN** un usuario `MEMBER` consulta las citas de la semana
- **THEN** el sistema devuelve las citas de su organización activa en ese rango

#### Scenario: Cita de otra clínica
- **WHEN** un usuario pide, reprograma o cambia el estado de una cita que pertenece a otra organización
- **THEN** el sistema responde `404`
- **AND** no revela ni modifica nada

#### Scenario: Sin sesión o sin organización activa
- **WHEN** un visitante sin sesión, un token sin organización activa o una sesión de plataforma llama a la API de citas o de horario
- **THEN** el sistema responde `401`

#### Scenario: Membresía inactiva
- **WHEN** un usuario cuya membresía en la organización quedó inactiva consulta o modifica citas de esa organización
- **THEN** el sistema lo rechaza como a cualquier operación de negocio de esa organización
- **AND** no ve ni modifica ninguna cita

#### Scenario: Paciente de otra clínica
- **WHEN** un miembro agenda o reprograma indicando un paciente de otra organización
- **THEN** el sistema responde `404` como si el paciente no existiera
- **AND** no crea ni modifica la cita

#### Scenario: Odontólogo de otra clínica
- **WHEN** un miembro agenda o reprograma indicando como odontólogo a un usuario que no es miembro de la organización activa
- **THEN** el sistema responde `409` indicando que no es un odontólogo agendable
- **AND** no crea ni modifica la cita

### Requirement: Agendar una cita

El sistema SHALL crear una cita `AGENDADA` con paciente, odontólogo, inicio (un instante) y duración, y motivo
opcional (hasta 500 caracteres). El paciente SHALL existir en la organización y estar activo. El inicio SHALL ser
futuro y caer en múltiplo de 5 minutos de la hora de la clínica; la duración SHALL ser múltiplo de 5 entre 10 y 240
minutos. Días, horas y horario se evalúan en la zona horaria de la clínica.

#### Scenario: Cita válida
- **WHEN** un miembro agenda a un paciente activo con un odontólogo habilitado, el martes próximo a las 10:00, 30 minutos, dentro del horario y sin choques
- **THEN** el sistema crea la cita en estado `AGENDADA` con fin a las 10:30
- **AND** la cita aparece en la agenda de esa semana

#### Scenario: Paciente dado de baja
- **WHEN** se agenda a un paciente archivado
- **THEN** el sistema responde `409` indicando que el paciente no está activo

#### Scenario: Paciente inexistente
- **WHEN** se agenda a un paciente que no existe en la organización
- **THEN** el sistema responde `404`

#### Scenario: Inicio en el pasado
- **WHEN** se agenda con un inicio anterior al momento actual
- **THEN** el sistema responde `422` indicando que la cita debe ser futura

#### Scenario: Duración o minuto fuera de la grilla
- **WHEN** se agenda con duración 7, 0 o 300 minutos, o con inicio a las 10:03
- **THEN** el sistema responde `400` con el campo inválido
- **AND** el formulario muestra el mismo error sin llamar a la API

### Requirement: Odontólogo con capacidad clínica

Toda cita SHALL tener un odontólogo, y ese odontólogo SHALL ser miembro activo de la organización con capacidad
clínica (credencial declarada, no retirada) vigente en la fecha de la cita, en hora de Perú. El sistema SHALL
ofrecer la lista de odontólogos agendables de la organización activa.

#### Scenario: Odontólogo sin credencial o con la capacidad retirada
- **WHEN** se agenda con un miembro sin credencial, con la capacidad retirada o con la membresía inactiva
- **THEN** el sistema responde `409` con el motivo
- **AND** no crea la cita

#### Scenario: Credencial que vence antes de la cita
- **WHEN** la credencial del odontólogo está habilitada hasta el 30 de noviembre y la cita es el 2 de diciembre
- **THEN** el sistema responde `409` indicando que la credencial no está vigente en la fecha de la cita

#### Scenario: Lista de odontólogos agendables
- **WHEN** un miembro abre el formulario de agendar
- **THEN** el selector de odontólogo lista solo los miembros con capacidad clínica vigente hoy

#### Scenario: Sin odontólogo
- **WHEN** se agenda sin indicar odontólogo
- **THEN** el sistema responde `400`

### Requirement: Sin choques en la agenda del odontólogo

El sistema SHALL rechazar una cita cuyo intervalo se solape con otra cita `AGENDADA` o `ATENDIDA` del mismo
odontólogo. Las citas `CANCELADA` y `NO_ASISTIO` SHALL liberar su hueco. Una cita que empieza justo cuando otra
termina no es choque. La garantía SHALL valer también ante reservas simultáneas.

#### Scenario: Solape parcial
- **WHEN** el odontólogo tiene una cita de 10:00 a 10:30 y se agenda otra de 10:15 a 10:45 con él
- **THEN** el sistema responde `409` indicando el choque
- **AND** no crea la cita

#### Scenario: Citas contiguas
- **WHEN** el odontólogo tiene una cita de 10:00 a 10:30 y se agenda otra de 10:30 a 11:00 con él
- **THEN** el sistema crea la cita

#### Scenario: Hueco liberado por una cancelación
- **WHEN** la cita de 10:00 a 10:30 se canceló y se agenda otra en ese mismo horario con el mismo odontólogo
- **THEN** el sistema crea la cita

#### Scenario: Otro odontólogo en el mismo horario
- **WHEN** se agenda de 10:00 a 10:30 con un odontólogo distinto al de una cita existente en ese horario
- **THEN** el sistema crea la cita

#### Scenario: Dos reservas simultáneas del mismo hueco
- **WHEN** dos miembros agendan a la vez el mismo odontólogo en intervalos que se solapan
- **THEN** exactamente una cita se crea y la otra solicitud recibe `409`

### Requirement: Cita dentro del horario de atención

El sistema SHALL aceptar una cita solo si su intervalo completo cae dentro de un mismo tramo del horario de
atención del día de la semana de su inicio, en la zona horaria de la clínica. Un día sin tramos SHALL estar
cerrado.

#### Scenario: Cita que excede el cierre
- **WHEN** el tramo del lunes es 09:00–13:00 y se agenda el lunes de 12:45 a 13:15
- **THEN** el sistema responde `422` indicando que la cita queda fuera del horario de atención

#### Scenario: Cita que cruza el turno partido
- **WHEN** el lunes tiene los tramos 09:00–13:00 y 15:00–19:00 y se agenda de 12:30 a 15:30
- **THEN** el sistema responde `422`

#### Scenario: Día cerrado
- **WHEN** el domingo no tiene tramos y se agenda un domingo
- **THEN** el sistema responde `422` indicando que la clínica no atiende ese día

### Requirement: Horario de atención configurable por el administrador

El sistema SHALL guardar por organización, para cada día de la semana, cero o más tramos de apertura y cierre en
múltiplos de 5 minutos, con apertura anterior al cierre y sin solaparse entre sí. Solo un `ADMIN` SHALL
modificarlo; todo miembro SHALL poder leerlo. Cada organización SHALL nacer con un horario por defecto editable.

#### Scenario: El administrador define un turno partido
- **WHEN** un `ADMIN` guarda para el lunes los tramos 09:00–13:00 y 15:00–19:00
- **THEN** el sistema guarda el horario y la agenda sombrea de 13:00 a 15:00 del lunes

#### Scenario: Tramos inválidos
- **WHEN** un `ADMIN` guarda un tramo 14:00–10:00, uno 09:00–13:00 junto con otro 12:00–15:00, o uno 09:07–12:00
- **THEN** el sistema responde `400` sin guardar ningún cambio del horario

#### Scenario: El horario es siempre el de la organización activa
- **WHEN** un `ADMIN` de la organización A lee o guarda el horario de atención
- **THEN** el sistema lee o modifica solo el horario de A, sin que la petición pueda indicar otra organización
- **AND** el horario de la organización B no cambia

#### Scenario: Un miembro intenta cambiar el horario
- **WHEN** un `MEMBER` intenta guardar el horario de atención
- **THEN** el sistema responde `403`

#### Scenario: Horario por defecto
- **WHEN** se crea una organización nueva
- **THEN** su horario es lunes a viernes 09:00–18:00 y sábado 09:00–13:00, y el domingo está cerrado

#### Scenario: Cambiar el horario no toca las citas existentes
- **WHEN** el `ADMIN` acorta el sábado a 09:00–12:00 y había una cita `AGENDADA` el sábado de 12:00 a 12:30
- **THEN** la cita sigue `AGENDADA` y visible en la agenda
- **AND** reprogramarla exige que el nuevo intervalo caiga en el horario vigente

### Requirement: Reprogramar una cita agendada

El sistema SHALL permitir cambiar inicio, duración y odontólogo de una cita `AGENDADA`, conservando la misma cita
y su paciente, con las mismas validaciones que al agendar (futura, grilla, capacidad, horario, sin choques
excluyendo a la propia cita). Una cita en estado final SHALL no reprogramarse.

#### Scenario: Mover la cita a otro día
- **WHEN** se reprograma una cita `AGENDADA` del martes 10:00 al jueves 16:00 con el mismo odontólogo
- **THEN** la cita conserva su identificador y su paciente, sigue `AGENDADA` y aparece el jueves a las 16:00

#### Scenario: Alargar la cita sobre su propio horario
- **WHEN** una cita de 10:00 a 10:30 se reprograma a 10:00–11:00 y no hay otra cita del odontólogo en ese intervalo
- **THEN** el sistema acepta el cambio sin considerarla un choque consigo misma

#### Scenario: Reprogramar a un hueco ocupado
- **WHEN** se reprograma a un intervalo que se solapa con otra cita vigente del odontólogo
- **THEN** el sistema responde `409` y la cita queda como estaba

#### Scenario: Reprogramar una cita cerrada
- **WHEN** se intenta reprogramar una cita `ATENDIDA`, `NO_ASISTIO` o `CANCELADA`
- **THEN** el sistema responde `409` indicando que la cita ya está cerrada

### Requirement: Ciclo de estados de la cita

Una cita `AGENDADA` SHALL poder pasar a `CANCELADA` en cualquier momento, con un motivo opcional de hasta 500
caracteres, y a `ATENDIDA` o `NO_ASISTIO` solo cuando su hora de inicio ya llegó. `ATENDIDA`, `NO_ASISTIO` y
`CANCELADA` SHALL ser estados finales. El sistema SHALL registrar quién y cuándo hizo cada cambio de estado.

#### Scenario: Cancelar con motivo
- **WHEN** un miembro cancela una cita `AGENDADA` con el motivo "El paciente pidió otro día"
- **THEN** la cita queda `CANCELADA` con ese motivo, quién la canceló y cuándo
- **AND** su hueco queda libre

#### Scenario: Marcar asistencia antes de la hora
- **WHEN** se marca `ATENDIDA` o `NO_ASISTIO` una cita cuyo inicio todavía no llegó
- **THEN** el sistema responde `409` indicando que la cita aún no empezó

#### Scenario: Paciente que no vino
- **WHEN** pasada la hora de inicio un miembro marca la cita como `NO_ASISTIO`
- **THEN** la cita queda `NO_ASISTIO` y su hueco queda libre

#### Scenario: Cambiar un estado final
- **WHEN** se intenta cancelar, marcar atendida o marcar no asistió una cita que ya está en un estado final
- **THEN** el sistema responde `409` y la cita no cambia

### Requirement: Atender la cita con nota de consulta

Desde una cita `AGENDADA` cuyo inicio ya llegó, o `ATENDIDA` sin nota, el sistema SHALL abrir la nota de consulta
con la atención igual al inicio de la cita, enlazarla y dejar la cita `ATENDIDA`, todo o nada. SHALL exigir
capacidad clínica vigente a quien lo hace y las precondiciones de la nota. Una cita SHALL tener a lo sumo una nota
enlazada a la vez; si se descarta, puede enlazar otra.

#### Scenario: Atender con nota
- **WHEN** a las 10:05 un miembro elige "Atender con nota" en la cita de las 10:00 de un paciente con la Historia Clínica completada
- **THEN** el sistema abre una nota de consulta `ABIERTA` con la atención a las 10:00, la enlaza a la cita y la cita queda `ATENDIDA`
- **AND** la agenda ofrece ir a esa nota desde la cita

#### Scenario: Quien atiende no tiene capacidad clínica
- **WHEN** un miembro sin credencial, con la capacidad retirada o vencida elige "Atender con nota"
- **THEN** el sistema responde con acceso denegado (`403`) y el motivo de capacidad clínica
- **AND** la cita no cambia y no se crea ninguna nota
- **AND** la interfaz no le ofrece la acción, pero sí "marcar atendida"

#### Scenario: La clínica retira la capacidad mientras se atiende con nota
- **WHEN** el administrador retira la capacidad clínica de un miembro al mismo tiempo que ese miembro elige "Atender con nota"
- **THEN** o "Atender con nota" termina primero y la nota queda abierta y enlazada, o el retiro termina primero y "Atender con nota" se rechaza sin cambios

#### Scenario: Paciente sin Historia Clínica completada
- **WHEN** se elige "Atender con nota" para un paciente cuya Historia Clínica no está completada
- **THEN** el sistema responde `409` con el motivo de la nota de consulta
- **AND** la cita sigue `AGENDADA` y no se crea ninguna nota

#### Scenario: Ya hay una nota abierta
- **WHEN** el paciente ya tiene una nota de consulta abierta
- **THEN** el sistema responde `409` y la cita no cambia

#### Scenario: Cita ya enlazada o que no empezó
- **WHEN** se elige "Atender con nota" en una cita que ya tiene una nota enlazada, o antes de su hora de inicio
- **THEN** el sistema responde `409` y no abre otra nota

#### Scenario: La nota enlazada se descarta
- **WHEN** la nota abierta desde la cita se descarta vacía
- **THEN** la cita sigue `ATENDIDA` y queda sin nota
- **AND** puede volver a atenderse con nota

#### Scenario: Marcar atendida sin nota
- **WHEN** un miembro marca `ATENDIDA` la cita de la primera visita, que se usa para la admisión
- **THEN** la cita queda `ATENDIDA` sin nota
- **AND** más tarde puede atenderse con nota si el paciente ya cumple las precondiciones

### Requirement: Agendar la próxima cita propuesta en una nota

Cuando una nota de consulta del paciente propone una fecha de próxima cita, la interfaz SHALL ofrecer agendarla,
abriendo el formulario de agendar con ese paciente y esa fecha precargados y sin crear nada hasta que el usuario
confirme.

#### Scenario: Agendar desde la nota
- **WHEN** el usuario elige "Agendar próxima cita" en una nota con próxima cita el 5 de diciembre
- **THEN** se abre el formulario de agendar con el paciente y el 5 de diciembre precargados
- **AND** el odontólogo, la hora y la duración los elige el usuario

#### Scenario: Nota sin próxima cita
- **WHEN** la nota no propone próxima cita
- **THEN** la interfaz no muestra la acción

### Requirement: Vista semanal de la agenda

La interfaz SHALL mostrar la agenda por semana (lunes a domingo) con un bloque por cita en su día y hora, con el
paciente, el odontólogo (con un color propio por odontólogo) y el estado, y SHALL sombrear las horas fuera del
horario de atención. SHALL permitir navegar entre semanas y filtrar por odontólogo. En pantallas angostas SHALL
mostrar un día por vez.

#### Scenario: Ver la semana
- **WHEN** un miembro abre la Agenda
- **THEN** ve la semana actual con las citas de todos los odontólogos y las horas fuera del horario sombreadas

#### Scenario: Navegador en otra zona horaria
- **WHEN** la clínica está en Lima y el usuario abre la Agenda desde un equipo configurado en otra zona horaria
- **THEN** la cita de las 10:00 de Lima se muestra a las 10:00, y un hueco elegido a las 10:00 se agenda a las 10:00 de Lima

#### Scenario: Hora inexistente o repetida por cambio de horario
- **WHEN** la clínica está en una zona con horario de verano y se elige una hora que ese día no existe o se repite
- **THEN** el formulario muestra, antes de confirmar, la hora resultante con su desfase: la inexistente avanzada al primer instante válido y la repetida en su primera ocurrencia

#### Scenario: Agendar desde un hueco libre
- **WHEN** el usuario hace clic en un hueco libre dentro del horario
- **THEN** se abre el formulario de agendar con ese día y esa hora precargados

#### Scenario: Detalle de una cita
- **WHEN** el usuario hace clic en una cita
- **THEN** ve su detalle y solo las acciones que su estado y su hora permiten (reprogramar, cancelar, marcar atendida, no asistió, atender con nota, ir a la nota)

#### Scenario: Filtrar por odontólogo
- **WHEN** el usuario filtra por un odontólogo
- **THEN** la semana muestra solo las citas de ese odontólogo

#### Scenario: Rango de consulta acotado
- **WHEN** se piden a la API citas de un rango mayor a 7 días o con el fin antes del inicio
- **THEN** el sistema responde `400`

#### Scenario: Celular
- **WHEN** la Agenda se abre en una pantalla angosta
- **THEN** se ve un solo día con controles para pasar al anterior y al siguiente
