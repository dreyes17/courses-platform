# Courses — plataforma de cursos online orientada a eventos

Backend para la prueba técnica de Senior Backend Engineer: catálogo de cursos, inscripciones con aforo
limitado, pagos con confirmación asíncrona y emisión de certificados por eventos, comunicados mediante
RabbitMQ.

Stack: Java 21 · Spring Boot 4.1 · PostgreSQL · Flyway · Spring AMQP · Spring Security (JWT) · Testcontainers.

## Arrancar el proyecto

Este repo incluye un devcontainer (ver [`.devcontainer/README.md`](.devcontainer/README.md)) con Postgres,
RabbitMQ y Redis ya levantados y las variables de entorno de Spring preconfiguradas. Dentro de él:

```bash
./mvnw test              # compila, migra el esquema en Testcontainers y valida el contexto de Spring
./mvnw spring-boot:run    # arranca contra los servicios del devcontainer
```

Con la aplicación arrancada, la documentación interactiva de la API está en
<http://localhost:8080/swagger-ui.html> (especificación OpenAPI en `/v3/api-docs`).

## Arquitectura

El código se organiza por *bounded context* (no por capa técnica). Dentro de cada uno, `domain` contiene
las entidades y sus invariantes, `repository` el acceso a datos, `application` los casos de uso (dueños de
las transacciones, devuelven vistas `record`, nunca entidades), `web` los controladores REST y
`messaging` los listeners de RabbitMQ. Controladores y listeners son adaptadores finos: validan o leen la
entrada, delegan en `application` y traducen el resultado.

| Paquete | Responsabilidad |
|---|---|
| `catalog` | Categorías, instructores y cursos — CRUD, publicar/archivar, búsqueda combinable (`CourseSpecifications`) y reserva atómica de plazas (`CourseRepository.tryReserveSeat`) |
| `enrollment` | Estudiantes e inscripciones — máquina de estados `PENDING_PAYMENT → ACTIVE → COMPLETED` / `CANCELLED`; `EnrollmentService` (inscribir, progreso, cancelar, listados) y `PaymentOutcomeHandler` (reacción a `PaymentConfirmed`/`PaymentFailed`) |
| `payment` | Pagos — `PENDING → CONFIRMED` / `FAILED`; `PaymentProcessor` consume `EnrollmentCreated` y cobra contra una pasarela simulada |
| `certificate` | `CertificateIssuer` consume `EnrollmentCompleted` y emite el certificado |
| `messaging.events` | Contrato de eventos: `sealed interface DomainEvent` + un `record` por evento, y `EventType` (nombre, routing key, versión) |
| `messaging.outbox` | `OutboxRecorder` (escribe eventos en la transacción de negocio) y `OutboxRelay` (los publica en RabbitMQ) |
| `messaging.inbox` | Deduplicación del lado consumidor (`processed_events`) y lectura de mensajes entrantes |
| `messaging.config` | Topología RabbitMQ declarada por código |
| `idempotency` | Idempotencia a nivel HTTP para la cabecera `Idempotency-Key` |
| `shared` | `BaseEntity`, jerarquía de excepciones de dominio, `GlobalExceptionHandler` (errores RFC 9457), `PageResponse` y configuración (OpenAPI, seguridad) |

Las entidades son modelos ricos: los cambios de estado válidos viven como métodos en la propia entidad
(`Course.publish()`, `Enrollment.cancel()`, `Payment.confirm()`, ...) y lanzan una excepción de dominio
específica ante una transición inválida, en vez de exponer setters y dejar la validación a quien la llame.

El esquema de base de datos vive en `src/main/resources/db/migration` (Flyway, `ddl-auto: validate`) y
replica en la propia BD las invariantes críticas: `courses` tiene `CHECK (seats_taken <= capacity)`, y
`enrollments` tiene un índice único parcial que impide que un mismo estudiante tenga dos inscripciones
`PENDING_PAYMENT`/`ACTIVE` para el mismo curso a la vez.

## API REST

Todos los endpoints cuelgan de `/api` y están documentados en Swagger UI con sus parámetros y códigos de
respuesta.

| Recurso | Operaciones |
|---|---|
| `/api/categories` | crear, listar, obtener, renombrar (`PUT`), `POST /{id}/archive`, `POST /{id}/activate`, borrar (409 si tiene cursos) |
| `/api/instructors` | crear (email único), listar, obtener, actualizar perfil, borrar (409 si tiene cursos) |
| `/api/courses` | crear (en `DRAFT`), buscar, obtener, actualizar, `POST /{id}/publish`, `POST /{id}/archive`, borrar (solo `DRAFT`) |
| `/api/students` | registrar, listar, obtener |
| `/api/enrollments` | inscribir (`Idempotency-Key` obligatoria), obtener, `PUT /{id}/progress`, `POST /{id}/cancel` |
| `/api/courses/{id}/enrollments` | estudiantes inscritos en un curso |
| `/api/students/{id}/enrollments` | cursos de un estudiante |

- **Transiciones de estado como acciones.** Publicar, archivar y cancelar son `POST` sobre un subrecurso, no
  un `PUT` que cambie el campo `status`. Así la regla de negocio de cada transición vive en un único método
  del dominio.
- **Paginación en todos los listados.** Aceptan `page`, `size` (por defecto 20, máximo 100) y `sort`
  (propiedades de la entidad, p. ej. `sort=price,desc`). Responden con
  `{content, page, size, totalElements, totalPages}`. Ningún endpoint devuelve una tabla entera.
- **Búsqueda de cursos.** Todos los filtros son opcionales y combinables: `categoryId`, `level`, `minPrice`,
  `maxPrice`, `title` (subcadena sin distinguir mayúsculas) y `withAvailableSeats=true`, además de `status`.
- **Sin N+1 en los listados relacionales.** Cursos con su categoría e instructor, estudiantes de un curso y
  cursos de un estudiante se cargan con `@EntityGraph` sobre relaciones *to-one*, así que la paginación
  sigue haciéndose en SQL. `QueryEfficiencyTest` cuenta las sentencias SQL del hilo: cada página cuesta como
  máximo 2 consultas, sea cual sea su tamaño.

### Errores

`GlobalExceptionHandler` responde siempre `application/problem+json` (RFC 9457) con `title`, `detail`,
`status` e `instance`. Solo llegan al cliente mensajes escritos por la aplicación: cualquier excepción
inesperada se registra en el servidor y se devuelve como un 500 genérico.

| Situación | Excepción | HTTP |
|---|---|---|
| Cuerpo inválido (Bean Validation) | `MethodArgumentNotValidException` → incluye `errors` por campo | 400 |
| Cabecera obligatoria ausente, `sort` sobre una propiedad inexistente | `MissingRequestHeaderException`, `PropertyReferenceException` | 400 |
| Recurso inexistente | `ResourceNotFoundException` | 404 |
| Curso lleno, doble inscripción, transición de estado inválida, duplicado, recurso en uso | subclases de `ConflictException` | 409 |
| Modificación concurrente, violación de restricción en BD | `OptimisticLockingFailureException`, `DataIntegrityViolationException` | 409 |
| Regla de negocio (progreso hacia atrás, aforo menor que las plazas ocupadas, categoría archivada) | `BusinessRuleViolationException` | 422 |
| `Idempotency-Key` reutilizada con otra petición | `IdempotencyKeyReusedException` | 422 |

El dominio no lanza `IllegalArgumentException` para reglas de negocio: tiene su propia jerarquía de
excepciones. Así el manejador puede traducir cada caso con precisión sin capturar excepciones genéricas del
framework, cuyo mensaje podría revelar detalles internos.

## Flujo de inscripción

```
POST inscripción ──► EnrollmentService.enroll  (una transacción)
                      ├─ reserva plaza (UPDATE condicional)
                      ├─ Enrollment PENDING_PAYMENT + Payment PENDING
                      └─ outbox: EnrollmentCreated
OutboxRelay ──► courses.events ──enrollment.created──► PaymentProcessor
                                                         ├─ aprobado → Payment CONFIRMED + outbox PaymentConfirmed
                                                         └─ rechazado → Payment FAILED  + outbox PaymentFailed
            ──payment.confirmed──► PaymentOutcomeHandler → Enrollment ACTIVE
            ──payment.failed─────► PaymentOutcomeHandler → Enrollment CANCELLED + libera plaza
EnrollmentService.updateProgress(100) → Enrollment COMPLETED + outbox EnrollmentCompleted
            ──enrollment.completed─► CertificateIssuer → Certificate
```

Cada flecha hacia RabbitMQ sale del outbox, nunca de un `send` directo dentro de la petición.

## Concurrencia: reserva de plazas

**Estrategia elegida: actualización atómica condicional en SQL.**

```sql
UPDATE courses SET seats_taken = seats_taken + 1, version = version + 1
 WHERE id = ? AND status = 'PUBLISHED' AND seats_taken < capacity
```

Si la sentencia actualiza 0 filas, no hay plaza (o el curso no está publicado). Entonces el servicio carga el
curso y `Course.assertAcceptsEnrollment()` decide qué excepción de dominio corresponde
(`CourseFullException` → 409, `InvalidCourseStateException`).

Por qué esta y no las otras dos que acepta el enunciado:

- **Frente al bloqueo optimista (`@Version` + reintento):** bajo contención alta, como en un lanzamiento de
  curso popular, casi todas las transacciones fallarían y reintentarían. La sentencia condicional nunca
  necesita reintento: Postgres serializa las escrituras sobre la fila y cada una evalúa el `WHERE` con el
  valor ya actualizado.
- **Frente al bloqueo pesimista (`SELECT ... FOR UPDATE`):** da la misma garantía, pero con dos viajes a la
  BD y el bloqueo retenido mientras la aplicación decide. La sentencia condicional hace comprobación y
  escritura en un solo paso.
- **La BD es la última línea de defensa:** aunque un bug saltase la sentencia, `CHECK (seats_taken <= capacity)`
  rechazaría el exceso.

La columna `version` se mantiene y la sentencia la incrementa. El motivo: la edición de un curso por un
instructor usa bloqueo optimista y escribe todas las columnas, incluida `seats_taken`. Sin ese incremento,
una edición concurrente podría sobrescribir `seats_taken` con un valor leído antes de una reserva.

Reserva de plaza, inscripción, pago `PENDING` y evento en el outbox van en **la misma transacción**. Si algo
falla (por ejemplo, el estudiante no existe), el `UPDATE` se revierte y la plaza no se pierde.
`EnrollmentConcurrencyTest` lanza 20 estudiantes a la vez contra 3 plazas y comprueba que entran exactamente 3.

### Idempotencia de la petición (`Idempotency-Key`)

`IdempotentRequests` reserva la clave con `INSERT ... ON CONFLICT DO NOTHING` en la misma transacción que la
inscripción:

- **Primera petición:** inserta la clave, inscribe y guarda la respuesta (el id de la inscripción).
- **Reintento con la misma clave y la misma petición:** devuelve la inscripción original sin reservar otra plaza.
- **Misma clave con otra petición** (distinto estudiante o curso, comparado por hash SHA-256):
  `IdempotencyKeyReusedException` (409/422).
- **Dos reintentos simultáneos:** el segundo queda bloqueado en el `INSERT` hasta que el primero confirma, y
  entonces devuelve su resultado.
- **Si la inscripción falla:** la clave se revierte con ella, así que el cliente puede reintentar con la misma
  clave.

Aparte, el índice único parcial sobre `enrollments` cubre el caso de dos peticiones concurrentes del mismo
estudiante *con claves distintas*.

## Topología RabbitMQ

Declarada por código en `RabbitTopology`:

```
exchange courses.events (topic, durable)
  enrollment.created   ──► payments.enrollment-created        ──(DLX)──► payments.enrollment-created.dlq
  payment.confirmed    ──► enrollments.payment-confirmed      ──(DLX)──► enrollments.payment-confirmed.dlq
  payment.failed       ──► enrollments.payment-failed         ──(DLX)──► enrollments.payment-failed.dlq
  enrollment.completed ──► certificates.enrollment-completed  ──(DLX)──► certificates.enrollment-completed.dlq

exchange courses.events.dlx (direct, durable) — routing key = <cola>.dlq
```

- **Una cola por consumidor:** un evento puede tener varios suscriptores independientes. Añadir uno nuevo es
  añadir una cola y un binding, sin tocar al emisor.
- **Reintentos:** `spring.rabbitmq.listener.simple.retry` hace 3 reintentos con backoff exponencial
  (1s, 2s, 4s, máximo 10s). Después, `default-requeue-rejected: false` rechaza el mensaje sin reencolarlo y el
  broker lo desvía a su DLQ. Así un mensaje envenenado no bloquea la cola.
- **Mensajes mal formados:** si falta el `messageId` o el JSON no es válido, `InboundEventReader` lanza
  `AmqpRejectAndDontRequeueException`, porque ese mensaje no va a funcionar nunca por mucho que se reintente.
- **Metadatos de cada mensaje:** `messageId` = id del evento (clave de deduplicación), `type` = nombre del
  evento, `timestamp`, `correlationId` = id del agregado, y las cabeceras `x-event-version` y
  `x-aggregate-type`. El cuerpo es JSON generado a partir del `record` del evento, nunca de la entidad JPA.

## Mensajería: outbox transaccional y deduplicación (`processed_events`)

### El problema

Cuando el servicio de inscripciones confirma en Postgres un cambio de dominio (p. ej. crea un `Enrollment`
en `PENDING_PAYMENT`), también tiene que publicar un evento (`EnrollmentCreated`) en RabbitMQ. Si esa
publicación se hiciera directamente dentro de la misma petición HTTP, el commit en BD y el publish al
broker serían dos operaciones independientes, no atómicas:

- El commit en BD tiene éxito pero el publish falla (broker caído, timeout de red) → el evento se pierde
  para siempre; nadie llegará a activar la inscripción ni a cobrar.
- El publish tiene éxito pero la transacción de BD se revierte después → se publicó un evento sobre un
  estado que nunca llegó a existir.

### La solución: outbox transaccional

En vez de publicar directamente, la misma transacción que persiste el cambio de dominio inserta también una
fila en `outbox_events`, usando la misma conexión JDBC. Como el `INSERT` en `enrollments` y el `INSERT` en
`outbox_events` ocurren en la misma transacción, o se confirman los dos o no se confirma ninguno: no hay
ninguna ventana temporal en la que uno exista sin el otro.

Columnas de `outbox_events` y para qué sirve cada una:

- **`id` (UUID)** — identificador del evento. Se reutiliza como `messageId`/cabecera de correlación al
  publicarlo en RabbitMQ, y es la mitad de la clave de deduplicación en `processed_events`.
- **`aggregate_type` / `aggregate_id`** — qué entidad de dominio originó el evento (p. ej. `"Enrollment"` +
  el UUID de la inscripción). Sirve para trazabilidad y depuración, y para un futuro feed de eventos por
  agregado.
- **`event_type`** — nombre del evento de dominio (`EnrollmentCreated`, `PaymentConfirmed`,
  `PaymentFailed`, `EnrollmentCompleted`), desacoplado del nombre de la clase Java para poder versionar el
  contrato del evento sin acoplarlo al código interno.
- **`payload` (JSONB)** — el cuerpo del evento ya serializado, construido a partir de un DTO propio del
  contrato de eventos, nunca serializando la entidad JPA directamente; así un refactor del modelo interno no
  rompe el contrato publicado.
- **`status` (`PENDING`/`PUBLISHED`/`FAILED`)** — `OutboxRelay` se ejecuta cada 500 ms y funciona así:
  - lee hasta 100 filas `PENDING` ordenadas por `created_at` (de ahí el índice compuesto
    `idx_outbox_events_status_created`) con `FOR UPDATE SKIP LOCKED`, de modo que varias instancias de la
    aplicación pueden ejecutar el relay sin publicar las mismas filas a la vez;
  - publica cada una con *publisher confirms* y solo la marca `PUBLISHED` cuando el broker confirma;
  - si el broker no confirma o no responde, la fila sigue `PENDING`, el lote se detiene y se reintenta en el
    siguiente ciclo. Un reinicio a mitad de camino no pierde el evento;
  - si el mensaje no tiene ninguna cola que lo reciba (*returned*, porque se publica con `mandatory`), la fila
    pasa a `FAILED`. Eso es un error de configuración que tiene que revisar una persona; reintentarlo en bucle
    no lo arreglaría.
- **`created_at` / `published_at`** — ordenan la cola de publicación y permiten medir el retraso entre "algo
  ocurrió" y "salió al bus", una métrica de observabilidad razonable para el bonus de Micrometer.

Esta estrategia da **entrega al menos una vez** (*at-least-once*): un evento puede publicarse más de una vez
(por ejemplo si el relay lo publica pero se cae antes de marcar `PUBLISHED`), pero nunca se pierde.

### El lado consumidor: `processed_events`

Como la garantía es "al menos una vez", un listener de RabbitMQ puede recibir el mismo mensaje dos veces
(redelivery del broker, reinicio del consumidor antes del ack, etc.). Sin protección, eso duplicaría
efectos — doble activación de una inscripción, doble certificado — justo lo que las secciones 5.3 y 7.2 del
enunciado prohíben explícitamente.

`processed_events` es la tabla de deduplicación del lado consumidor. Antes de aplicar el efecto de un
evento, el listener comprueba, **dentro de la misma transacción** que va a aplicar ese efecto, si ya existe
una fila para `(event_id, consumer_name)`:

- si existe, el mensaje es un duplicado: se descarta sin reaplicar el efecto (y se hace `ack`);
- si no existe, se aplica el efecto de negocio y se inserta la fila, en la misma transacción.

La comprobación y la inserción son una sola sentencia, `INSERT ... ON CONFLICT DO NOTHING`
(`IdempotentConsumer.isFirstDelivery`), no un `SELECT` seguido de un `INSERT`. Así, si llegan dos entregas
del mismo mensaje a la vez, la segunda se queda esperando la fila sin confirmar de la primera. Cuando la
primera confirma, la segunda recibe "0 filas" y se descarta; si la primera se revierte, la segunda procesa el
evento.

Los consumidores también comprueban el estado antes de actuar, como segunda defensa. Por ejemplo,
`CertificateIssuer` no emite si ya existe un certificado para esa inscripción, y la restricción `UNIQUE`
sobre `certificates.enrollment_id` lo garantiza en último término. Eso cubre incluso un mismo evento
republicado con otro id. `ConsumerIdempotencyTest` envía entregas duplicadas y comprueba que solo hay una
activación y un certificado.

La clave es compuesta (`event_id`, `consumer_name`) y no solo `event_id` porque el mismo evento puede tener
más de un consumidor interesado (por ejemplo `EnrollmentCompleted` lo consume hoy el emisor de
certificados, y mañana podría consumirlo también un servicio de notificaciones). Cada consumidor necesita su
propio registro de "ya procesé esto", independiente de los demás — con `event_id` como única clave, el
primer consumidor en procesar el evento bloquearía silenciosamente a los siguientes.

Insertar la fila de `processed_events` en la misma transacción que el efecto de negocio es lo que hace la
deduplicación fiable: si el efecto falla y la transacción se revierte, la fila tampoco se inserta, y el
mensaje se podrá reprocesar correctamente en el siguiente intento.

### Qué no resuelve `processed_events`

Los reintentos con backoff y la dead-letter queue (RabbitMQ, sección 7.2) cubren un problema distinto: un
mensaje que **nunca** se puede procesar con éxito (payload corrupto, referencia a un recurso que no existe).
Tras agotar los reintentos, ese mensaje se mueve a la DLQ en vez de bloquear la cola. `processed_events`
resuelve "este mensaje ya lo procesé, no lo proceses otra vez"; la DLQ resuelve "este mensaje no se puede
procesar, sácalo de la cola". Son mecanismos independientes y complementarios.

## Limitaciones conocidas

- **Pago confirmado de una inscripción ya cancelada.** Si el estudiante cancela mientras el cobro está en
  curso, el pago puede confirmarse igualmente. `PaymentProcessor` reduce esa ventana: no cobra si la
  inscripción ya no está `PENDING_PAYMENT`. Si aun así ocurre, se registra un aviso de "reembolso manual";
  el flujo de reembolso queda fuera de alcance.
- **Llamada a la pasarela dentro de la transacción.** `PaymentProcessor` llama a la pasarela con la
  transacción de BD abierta. Con la pasarela simulada no importa. Con una real, convendría separar el cobro
  de la transacción; la clave de idempotencia del pago (`enrollment-<id>`) ya evita cobrar dos veces si se
  reintenta.
- **Filas `FAILED` del outbox.** Requieren intervención manual; todavía no hay una alerta ni una métrica
  que las señale.

## Estado actual / pendiente

- [x] Esqueleto Spring Boot 4 + Testcontainers (Postgres, RabbitMQ)
- [x] Modelo de dominio y migración Flyway inicial (`V1__init.sql`)
- [x] Topología RabbitMQ (exchanges/colas/bindings, retry + DLQ) y relay del outbox
- [x] Casos de uso de inscripción/pago/certificado con reserva atómica de plazas
- [x] Tests de integración: concurrencia sobre el aforo, flujo completo por RabbitMQ, idempotencia de
      consumidores y de `Idempotency-Key`, mensaje envenenado a la DLQ
- [x] Endpoints REST con validación, paginación y OpenAPI; manejo de errores centralizado (ProblemDetail)
- [x] Tests HTTP end-to-end, de errores y de ausencia de N+1
- [ ] Seguridad JWT por rol. **Ahora mismo `SecurityConfig` permite todas las peticiones**, de forma
      provisional, hasta implementar la autenticación
- [ ] Tests unitarios de casos de uso con dobles de prueba
- [ ] Observabilidad (métricas de inscripciones/pagos/DLQ, logging con correlación) y `docker-compose` de la app
