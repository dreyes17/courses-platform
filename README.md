# Courses — plataforma de cursos online orientada a eventos

Backend para la prueba técnica de Senior Backend Engineer: catálogo de cursos, inscripciones con aforo
limitado, pagos con confirmación asíncrona y emisión de certificados por eventos, comunicados mediante
RabbitMQ.

Stack: Java 21 · Spring Boot 4.1 · PostgreSQL · Flyway · Spring AMQP · Spring Security (JWT) · Testcontainers.

## Arrancar el proyecto

### Con Docker Compose (solo necesita Docker)

```bash
cp .env.example .env          # secretos de evaluación local; .env está fuera de git
docker compose up --build     # PostgreSQL + RabbitMQ + aplicación
```

- La aplicación queda en <http://localhost:8080>. La documentación interactiva está en
  <http://localhost:8080/swagger-ui.html> y la especificación OpenAPI en `/v3/api-docs`.
- La interfaz de RabbitMQ está en <http://localhost:15672>, con las credenciales de `.env`. Sirve para ver
  las colas y las DLQ.
- Hay una cuenta ADMIN creada con `ADMIN_EMAIL`/`ADMIN_PASSWORD`. Ver [Seguridad](#seguridad) para obtener un
  token.
- `docker compose down -v` lo para todo y borra los volúmenes de datos.

> **Si lanzas `docker compose` desde dentro del devcontainer:** ese entorno usa su propio Docker
> (Docker-in-Docker), así que los puertos quedan publicados en el `localhost` del contenedor, no en el de tu
> máquina. Para abrir la API desde tu navegador, tu IDE o herramienta de devcontainers tiene que reenviar a tu
> máquina el puerto `8080` del contenedor. `devcontainer.json` lo declara en `forwardPorts`, pero no todos los
> IDE aplican esa configuración automáticamente. Si `http://localhost:8080` no responde, reenvía el puerto
> `8080` del contenedor con el mecanismo de tu IDE y usa la dirección local que te asigne, que puede no ser el
> 8080 si ese puerto ya está ocupado en tu máquina. Para la interfaz de RabbitMQ de este stack, haz lo mismo
> con el `15672`. Ojo: ese puerto de tu máquina puede estar apuntando ya a la RabbitMQ del propio devcontainer,
> que es otra instancia con otras credenciales.
>
> Si ejecutas `docker compose` directamente en tu máquina, fuera del devcontainer, no hace falta nada de esto.

Cómo se comporta el despliegue:

- **Secretos:** solo llegan por el entorno o por `.env`. Si falta alguno obligatorio, `docker compose` se
  detiene y dice cuál.
- **Orden de arranque:** la aplicación espera a que Postgres y RabbitMQ pasen su healthcheck. Su propio
  healthcheck consulta `/actuator/health/readiness` en el puerto de gestión (ver [Actuator](#actuator-y-puerto-de-gestión)).
- **Puertos:** solo se publica el 8080 (la API). Actuator escucha en el 8081, que solo es accesible dentro
  de la red de Docker.
- **Arranque de la aplicación:** Flyway crea el esquema y la topología de RabbitMQ se declara sola, sin
  pasos manuales.
- **Imagen:** el `Dockerfile` compila con el wrapper `mvnw` en una etapa JDK y ejecuta en una etapa JRE, con
  un usuario sin privilegios. Usa las capas de Spring Boot, así que un cambio de código no vuelve a enviar
  las dependencias.
- **Tests:** la imagen no ejecuta tests. Los de integración necesitan Docker (Testcontainers) y se lanzan
  aparte con `./mvnw test`.

### Entorno de desarrollo: devcontainer

El repositorio incluye un devcontainer (carpeta [`.devcontainer/`](.devcontainer/)): la definición completa
del entorno de desarrollo, versionada junto al código y basada en la especificación abierta
[Dev Containers](https://containers.dev). Al abrir el proyecto con él se obtiene un contenedor con todo lo
necesario ya instalado, configurado y arrancado.

**Qué incluye**

- **Herramientas:** Java 21, Maven, Docker propio (Docker-in-Docker), `psql`, `jq` y `httpie`.
- **Servicios:** PostgreSQL 18.6, RabbitMQ 4.3.6 (con su interfaz de gestión) y Redis, ya levantados.
- **Variables de entorno:** las `SPRING_*` que la aplicación necesita para conectarse a esos servicios, más
  valores de desarrollo para `JWT_SECRET` y `ADMIN_EMAIL`/`ADMIN_PASSWORD`.
- **Imágenes precargadas:** las de Testcontainers, descargadas en la primera creación.
- **Servidores MCP para asistentes de IA:** documentación actualizada de las librerías, inspección de la BD
  y del broker, y GitHub. Detalle en [`.devcontainer/README.md`](.devcontainer/README.md).

**Por qué conviene**

- **Reproducible:** quien revise el proyecto trabaja con las mismas versiones de Java, Maven, PostgreSQL y
  RabbitMQ que se usaron al desarrollarlo, y que coinciden con las de `docker-compose.yml` y Testcontainers.
  Desaparece el "en mi máquina funciona".
- **Sin instalar nada en tu máquina** salvo Docker, y sin interferir con lo que ya tengas. El Docker propio
  del contenedor aísla los contenedores de Testcontainers y de `docker compose`, y el JDK y las
  herramientas no se mezclan con las tuyas.
- **Listo desde el primer minuto:** los servicios ya están arriba y las variables inyectadas, así que
  `./mvnw spring-boot:run` arranca sin configurar nada. Con las imágenes precargadas, el primer
  `./mvnw test` no espera descargas.
- **Documentado como código:** el entorno está en el repositorio, se revisa en los commits y evoluciona con
  el proyecto, en lugar de depender de instrucciones de instalación que se quedan desactualizadas.

**Cómo usarlo**

1. Requisitos: Docker y un IDE o herramienta compatible con Dev Containers, como VS Code y sus derivados,
   los IDE de JetBrains o la CLI oficial `devcontainer`.
2. Opcional: `cp .devcontainer/.env.example .devcontainer/.env` para cambiar credenciales o añadir claves.
   Todo funciona sin ese fichero.
3. Abre el repositorio "en el contenedor" desde tu IDE, o con la CLI:
   `devcontainer up --workspace-folder .`. La primera construcción tarda unos minutos. Al terminar, un
   script comprueba las herramientas y que los servicios responden.
4. Dentro del contenedor:

   ```bash
   ./mvnw test              # unitarios + integración; Testcontainers levanta PostgreSQL y RabbitMQ
   ./mvnw spring-boot:run    # arranca contra los servicios del devcontainer
   ```

   La API queda en el puerto 8080 del contenedor. `devcontainer.json` pide reenviarlo a tu máquina; si tu
   IDE no lo hace solo, reenvíalo con su mecanismo de reenvío de puertos.

Las variables de entorno se fijan al crear el contenedor. Tras cambiar `.devcontainer/.env`, hay que
reconstruirlo; reiniciarlo no basta. Si no quieres reconstruir, puedes pasar una variable solo para un
arranque: `ADMIN_EMAIL=... ADMIN_PASSWORD=... ./mvnw spring-boot:run`.

**`docker compose up` y `./mvnw spring-boot:run` a la vez dentro del devcontainer.** Son dos formas
independientes de ejecutar la aplicación, y conviene saber cómo conviven:

- **Comparten puerto.** La app de Compose ya ocupa el 8080 del contenedor, así que `spring-boot:run` falla
  con *"Port 8080 was already in use"*. El 8081 no choca, porque Compose no lo publica.
- **No comparten datos.** `spring-boot:run` usa la PostgreSQL y la RabbitMQ del devcontainer (las variables
  `SPRING_*` apuntan a ellas). El stack de Compose tiene su propia base de datos y su propio broker, que no
  son accesibles desde fuera de su red. Lo que se crea en uno no aparece en el otro.

Según lo que necesites:

```bash
# Solo usar la API: basta con la app de Compose en el 8080; no hace falta spring-boot:run.

# Ejecutar desde el código (p. ej. para depurar): para la app de Compose y arranca la tuya
docker compose stop app          # o `docker compose down` para parar todo el stack
./mvnw spring-boot:run

# Tener las dos a la vez: arranca la tuya en otros puertos
SERVER_PORT=8090 MANAGEMENT_SERVER_PORT=8091 ./mvnw spring-boot:run
```

**Sin devcontainer** también funciona: basta con Java 21 y Docker en tu máquina. `./mvnw test` levanta sus
propios contenedores con Testcontainers, y `docker compose up --build` arranca la plataforma completa.

La aplicación **no arranca sin `JWT_SECRET`** (mínimo 32 bytes). Es a propósito: así nunca se ejecuta con una
clave por defecto. Si la arrancas con `./mvnw spring-boot:run` fuera del devcontainer, exporta antes esa
variable, junto con `ADMIN_EMAIL`/`ADMIN_PASSWORD` si quieres una cuenta ADMIN.

## Arquitectura

Es una **arquitectura por capas organizada por contexto** (*package-by-feature*): el código se agrupa
primero por *bounded context* y, dentro de cada uno, por capa. En cada contexto, `domain` contiene
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
| `identity` | Cuentas de usuario (`users`), registro de estudiantes, alta de instructores, login y emisión de JWT |
| `shared` | `BaseEntity`, jerarquía de excepciones de dominio, `GlobalExceptionHandler` (errores RFC 9457), `PageResponse`, OpenAPI y `shared.security` (filtros, JWT, reglas de acceso) |

### Separación de responsabilidades (punto 6 del enunciado)

| Requisito | Dónde | Cómo se cumple |
|---|---|---|
| Controladores REST finos | `<contexto>.web` (`CourseController`, `EnrollmentController`...) | Validan la entrada con Jakarta Validation, aplican `@PreAuthorize`, delegan en `application` y traducen a HTTP (`201` + `Location`, `204`...). No contienen reglas de negocio. |
| Servicios / casos de uso | `<contexto>.application` (`EnrollmentService`, `CourseService`, `PaymentProcessor`...) | Coordinan el caso de uso y son dueños de las transacciones (`@Transactional`). Las reglas de estado viven en las entidades. |
| Repositorios | `<contexto>.repository` | Spring Data JPA, más las consultas que requieren cuidado: el `UPDATE` atómico de plazas, `@EntityGraph` contra el N+1 y `Specification` para la búsqueda. |
| Entidades de dominio separadas de los DTOs | Entidades en `<contexto>.domain`; DTOs de entrada como `record` en `web` (`CatalogRequests`, `EnrollRequest`...); DTOs de salida como vistas `record` en `application` (`CourseView`, `EnrollmentView`...) | Ningún endpoint recibe ni devuelve una entidad JPA. |
| Adaptadores de mensajería aislados del dominio | Listeners en `<contexto>.messaging`; infraestructura común en `messaging` (`outbox`, `inbox`, `config`, `events`) | Cada listener solo lee el mensaje y delega en `application`. Los eventos son `record` propios, independientes de las entidades. |
| Manejo de errores centralizado | `shared.web.GlobalExceptionHandler`, apoyado en `shared.security.ProblemDetailsSecurityHandler` y en la jerarquía de `shared.domain` | Un único `@RestControllerAdvice` traduce cualquier excepción de un controlador a `problem+json`. Los 401/403 que corta la cadena de filtros, antes de llegar al controlador, salen con el mismo formato. El código HTTP lo decide el tipo de excepción (`ConflictException` → 409, `BusinessRuleViolationException` → 422). |

Con esta separación, la lógica de negocio no se concentra ni en los controladores ni en los listeners de
RabbitMQ, como exige el enunciado.

### Por qué por capas y no hexagonal

El enunciado valora tanto una arquitectura por capas bien separada como una hexagonal. Esta es la primera,
con algunos rasgos de la segunda allí donde aportan algo:

**Qué la separa de una hexagonal:**

- Las entidades de `domain` llevan anotaciones JPA. En hexagonal, el dominio sería Java puro y la
  persistencia se adaptaría a él.
- Los casos de uso usan directamente los repositorios de Spring Data, en lugar de interfaces propias
  (puertos de salida) implementadas por un adaptador JPA.
- Los controladores llaman a servicios concretos, sin interfaces de caso de uso (puertos de entrada).

**Por qué es una decisión deliberada:** en un proyecto de este tamaño, un dominio sin JPA obligaría a
duplicar cada entidad (modelo de dominio + entidad de persistencia + mapeo entre ambos), y cada repositorio
tendría una interfaz con una única implementación. Es coste sin beneficio real. Las invariantes están
protegidas igual: viven en los métodos de las entidades y se refuerzan en la BD.

**Donde sí hay puertos y adaptadores, porque ahí el intercambio es real:**

- **`PaymentGateway`** es un puerto de salida: una interfaz en `payment.application` con un adaptador
  intercambiable (`SimulatedPaymentGateway`). Pasar a una pasarela real consiste en añadir otro adaptador,
  sin tocar `PaymentProcessor`.
- **Los adaptadores de entrada** (controladores en `web`, listeners en `messaging`) solo traducen HTTP o
  AMQP y delegan. Añadir otra forma de entrada, como un servidor MCP, reutilizaría los mismos casos de uso.
- **El contrato de eventos** (`messaging.events`) es independiente de las entidades, así que el formato
  publicado no cambia al refactorizar el modelo interno.

Si el proyecto creciera, la migración a hexagonal sería incremental: extraer interfaces de repositorio
hacia `application` contexto a contexto, sin rehacer lo demás.

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
| `/api/auth` | `POST /register` (alta pública de estudiante), `POST /token` (login → JWT) |
| `/api/instructors` | crear instructor y su cuenta (email único), listar, obtener, actualizar perfil, borrar (409 si tiene cursos) |
| `/api/courses` | crear (en `DRAFT`), buscar, obtener, actualizar, `POST /{id}/publish`, `POST /{id}/archive`, borrar (solo `DRAFT`) |
| `/api/students` | listar, obtener |
| `/api/enrollments` | inscribir (`Idempotency-Key` obligatoria; el estudiante sale del token), obtener, `PUT /{id}/progress`, `POST /{id}/cancel` |
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
- **Correlación.** Cualquier petición puede enviar `X-Correlation-Id` (si no, se genera uno), y la respuesta
  siempre lo devuelve. Sirve para localizar en los logs todo lo que provocó esa petición (ver
  [Observabilidad](#observabilidad)).
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

## Seguridad

Autenticación con **JWT bearer** emitido por la propia aplicación, sin proveedor de identidad externo:

1. Un estudiante se registra en `POST /api/auth/register`. Los instructores los da de alta un ADMIN desde
   `POST /api/instructors`, contraseña incluida. El primer ADMIN se crea al arrancar a partir de
   `ADMIN_EMAIL`/`ADMIN_PASSWORD`.
2. `POST /api/auth/token` con email y contraseña devuelve un token HS256 firmado con `JWT_SECRET`, válido
   1 hora. Lleva `sub` (id de usuario), `roles` y, según el rol, `studentId` o `instructorId`.
3. Cada petición envía `Authorization: Bearer <token>`. La app lo valida como *resource server*: firma,
   caducidad y emisor `courses-api`.

```bash
TOKEN=$(curl -s localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d '{"email":"admin@courses.local","password":"dev-only-admin-password"}' | jq -r .accessToken)
curl localhost:8080/api/students -H "Authorization: Bearer $TOKEN"
```

**Autorización.** La cadena de filtros de la API solo distingue lo público (login, registro, Swagger) de lo
autenticado. Las reglas de rol y de propiedad van junto a cada endpoint con `@PreAuthorize`, apoyadas en
`AccessRules` (`@access.ownsEnrollment(...)`, `@access.teachesCourse(...)`), que hace consultas `exists`
ligeras:

| Rol | Puede |
|---|---|
| ADMIN | Todo: gestionar catálogo e instructores, ver cualquier estudiante, inscripción o listado |
| INSTRUCTOR | Crear cursos a su nombre; editar, publicar, archivar y borrar **sus** cursos; ver las inscripciones de **sus** cursos |
| STUDENT | Ver el catálogo (solo cursos `PUBLISHED`); inscribirse; ver, actualizar progreso y cancelar **sus** inscripciones |

- Al inscribirse, el estudiante se toma del token y el cuerpo solo lleva `courseId`. Así no es posible
  inscribir a otra persona.
- Sobre un recurso ajeno la respuesta es **403 aunque el id no exista**, para no revelar qué ids existen a
  quien no tiene acceso.
- 401 (sin token, token inválido o caducado, credenciales incorrectas) y 403 salen como `problem+json`,
  igual que el resto de errores.

**Contraseñas y secretos.**
- Las contraseñas se guardan solo como hash BCrypt (`{bcrypt}...`, mediante `DelegatingPasswordEncoder`,
  que permite migrar de algoritmo más adelante). Nunca se registran en logs.
- Un login con email inexistente también compara contra un hash de relleno. Así el tiempo de respuesta no
  revela qué emails tienen cuenta, y los dos casos devuelven el mismo mensaje.
- `JWT_SECRET` y las credenciales del ADMIN solo llegan por variables de entorno. En `application.yml` no
  hay ningún valor por defecto para el secreto.
- La migración `V2__user_accounts.sql` garantiza en la BD que cada rol esté vinculado exactamente a su
  perfil (estudiante, instructor o ninguno en el caso del ADMIN).

## Actuator y puerto de gestión

Actuator (`health`, `info`, `prometheus`) no se sirve en el puerto de la API. Tiene su propio puerto de
gestión: `management.server.port`, 8081 por defecto, configurable con `MANAGEMENT_SERVER_PORT`.

| Puerto | Qué sirve | Quién llega | Autenticación |
|---|---|---|---|
| 8080 | API y Swagger | clientes (publicado en Compose) | JWT, salvo login, registro y Swagger |
| 8081 | `/actuator/health`, `/actuator/info`, `/actuator/prometheus` | solo la red interna del despliegue (`expose`, sin `ports`) | ninguna |

**Por qué.** Prometheus y las sondas de un orquestador consultan estos endpoints cada pocos segundos con
una configuración fija y no saben obtener un JWT, que además caduca en una hora. Había tres opciones:

- dejar las métricas públicas en el 8080, lo que expone datos internos;
- darle al scraper una credencial fija, que es un secreto más que gestionar y rotar;
- aislar Actuator por red.

Elegí la tercera, que es la práctica habitual en contenedores. El aislamiento lo da la red: el 8081 nunca
se publica fuera, así que no hace falta autenticación. Por eso `health` muestra además el detalle de la BD
y de RabbitMQ.

**Sondas.** `/actuator/health/readiness` incluye la BD y RabbitMQ (`readinessState,db,rabbit`): una
instancia sin BD o sin broker deja de recibir tráfico. `/actuator/health/liveness` no los incluye a
propósito. Si se cayera la BD y la sonda de *liveness* dependiera de ella, el orquestador reiniciaría
procesos que están sanos, y eso no arreglaría nada.

**Cómo se aplica.**
- La cadena de seguridad de la API también se aplicaría al puerto de gestión, así que `SecurityConfig`
  define una cadena propia para él, con prioridad.
- Esa cadena solo se aplica cuando la petición llega al puerto **real** del servidor de gestión (lo guarda
  `ManagementPort` al arrancar) **y** su ruta está bajo `/actuator`. Así funciona también con un puerto
  aleatorio, y aunque alguien configurase el mismo puerto para API y gestión, esta cadena nunca podría abrir
  la API.

Con Compose, un Prometheus en la misma red leería `http://app:8081/actuator/prometheus`. Así se comprobó:

```bash
docker run --rm --network courses_default curlimages/curl -s http://app:8081/actuator/health
# {"status":"UP","components":{"db":{"status":"UP",...},"rabbit":{"status":"UP",...},...}}
```

## Observabilidad

### Métricas (Micrometer → `/actuator/prometheus`)

Además de las métricas automáticas de Spring Boot (JVM, HTTP, pool de conexiones, RabbitMQ), se exportan
estas métricas de negocio y operación:

| Métrica (nombre en Prometheus) | Tipo | Qué mide |
|---|---|---|
| `courses_enrollments_total{outcome}` | contador | Intentos de inscripción: `created`, `course_full`, `already_enrolled` |
| `courses_payments_processed_total{outcome}` | contador | Pagos procesados: `confirmed`, `failed` |
| `courses_certificates_issued_total` | contador | Certificados emitidos |
| `courses_messaging_dlq_messages{queue}` | *gauge* | Mensajes esperando en cada DLQ |
| `courses_outbox_events{status}` | *gauge* | Eventos del outbox `pending` (retraso de publicación) y `failed` (requieren intervención) |

- **Solo se cuenta lo que confirma.** Las inscripciones creadas, los pagos y los certificados se
  incrementan después del *commit*. Una operación que se revierte no infla la métrica, y un reintento
  tras un rollback no cuenta doble. Los rechazos sí se cuentan al momento: el rechazo es en sí el resultado.
- **Todas las series existen desde el arranque**, con valor 0. Un panel o una alerta no se encuentra con una
  serie que "aún no existe".
- **Los *gauges* se calculan en cada lectura de métricas:** consultan al broker (profundidad de cada DLQ) y
  a la BD (conteo por estado sobre un índice que ya existe). Si alguno no responde, el *gauge* vale `NaN` en
  lugar de romper la exportación del resto.

Alertas naturales sobre estas métricas: `courses_messaging_dlq_messages > 0`, `courses_outbox_events{status="failed"} > 0`
y un `pending` que crece de forma sostenida (el relay no consigue publicar).

### Logs con correlación

Cada flujo lleva un **`correlationId`** de principio a fin, incluso a través de RabbitMQ:

1. `CorrelationIdFilter` lo toma de la cabecera `X-Correlation-Id` de la petición (o genera uno), lo pone en
   el MDC de los logs y lo devuelve en la respuesta. Va antes de Spring Security, así que incluso los 401/403
   quedan correlacionados.
2. `OutboxRecorder` lo guarda en la fila del outbox (`V3__outbox_correlation_id.sql`), y `OutboxRelay` lo
   envía como cabecera AMQP `x-correlation-id`.
3. Cada consumidor lo restaura en el MDC mientras procesa el mensaje (`InboundEventReader.consume`), así que
   sus logs llevan el mismo id. Los eventos que emite lo heredan, y el id sigue viajando al siguiente paso.

Así, un `grep` por un id devuelve la petición HTTP, el cobro, la activación y el certificado de una misma
inscripción. `ObservabilityTest` lo comprueba: envía una inscripción con su id y verifica que el
`PaymentConfirmed`, que registra el consumidor de pagos, lleva ese mismo id.

Un id que llega del cliente solo se acepta si tiene un formato seguro (`[A-Za-z0-9._:-]{1,100}`); si no,
se sustituye por uno generado. Así nadie puede inyectar saltos de línea ni texto arbitrario en los logs.

**Formato.** En Compose, los logs salen en **JSON** (formato ECS, `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`),
una línea por evento y con `correlationId` como campo, listos para un recolector de logs. En desarrollo
local son texto plano, con el id entre corchetes. Por ejemplo:

```json
{"@timestamp":"…","log":{"level":"INFO","logger":"…PaymentProcessor"},"process":{"thread":{"name":"…RabbitListenerEndpointContainer#3-1"}},"correlationId":"demo-23643","message":"Payment … confirmed (transaction …)", …}
```

**Por qué un correlation-id y no trazas distribuidas.** El enunciado admite cualquiera de las dos. La
propagación automática de trazas no funcionaría bien aquí: el mensaje no se envía en la petición HTTP, sino
más tarde y desde otro hilo (el relay del outbox), con lo que la traza se rompería justo en el salto a
RabbitMQ. El correlation-id viaja guardado en el outbox y no depende de eso. Añadir OpenTelemetry (bonus)
sería complementario: habría que guardar también el contexto de traza en el outbox, igual que se hace con
el id.

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

## Tests

`./mvnw test` ejecuta los 91 tests en unos 30 segundos. Casi todos los de integración comparten un único
contexto de Spring y un único par de contenedores (`AbstractIntegrationTest`), por eso la suite es rápida pese
a usar PostgreSQL y RabbitMQ reales. La excepción es `ManagementPortTest`, que necesita servidores reales en
dos puertos distintos.

| Nivel | Qué cubre | Clases |
|---|---|---|
| **Unitarios de dominio** | Máquinas de estado e invariantes de `Course` y `Enrollment`, sin Spring ni mocks | `CourseTest`, `EnrollmentTest` |
| **Unitarios de casos de uso** (Mockito) | Ramas de error y lo que *no* debe ocurrir: no consumir plaza si ya está inscrito, no cobrar una inscripción cancelada, no reactivar una cancelada, no emitir un segundo certificado, entregas duplicadas sin efectos, login que no revela qué emails existen | `EnrollmentServiceTest`, `PaymentProcessorTest`, `PaymentOutcomeHandlerTest`, `CertificateIssuerTest`, `IdempotentRequestsTest`, `AccountServiceTest` |
| **Integración** (Testcontainers) | Concurrencia sobre el aforo (20 hilos, 3 plazas), flujo completo por RabbitMQ, idempotencia de consumidores con entregas duplicadas, mensaje envenenado → DLQ, `Idempotency-Key` | `EnrollmentConcurrencyTest`, `EnrollmentFlowTest`, `ConsumerIdempotencyTest` |
| **HTTP** (MockMvc) | Flujo end-to-end por la API con cada rol, mapeo de errores a `problem+json`, 401/403 y reglas de propiedad, ausencia de N+1 | `EnrollmentApiTest`, `ErrorHandlingApiTest`, `SecurityApiTest`, `QueryEfficiencyTest` |
| **Puertos reales** | Actuator accesible sin credenciales solo en el puerto de gestión, health y readiness con BD y broker, API protegida y ausente en ese puerto | `ManagementPortTest` |
| **Observabilidad** | `correlationId` propagado de la petición HTTP al consumidor a través de RabbitMQ, ids no seguros sustituidos, contadores por resultado, *gauges* de DLQ y de outbox `FAILED` | `ObservabilityTest` |
| **Contrato** | Campos del JSON de los eventos publicados | `EventContractTest` |

Para comprobar que los tests no pasan por casualidad, quité a propósito tres protecciones y confirmé que los
tests fallaban:

- sin el `@EntityGraph`, `QueryEfficiencyTest` detecta el N+1;
- sin la comprobación de estado en `PaymentProcessor`, `PaymentProcessorTest` detecta que se cobra una
  inscripción cancelada;
- sin la cabecera `x-correlation-id` en `OutboxRelay`, `ObservabilityTest` detecta que el id no llega al
  consumidor.

## Limitaciones conocidas

- **Pago confirmado de una inscripción ya cancelada.** Si el estudiante cancela mientras el cobro está en
  curso, el pago puede confirmarse igualmente. `PaymentProcessor` reduce esa ventana: no cobra si la
  inscripción ya no está `PENDING_PAYMENT`. Si aun así ocurre, se registra un aviso de "reembolso manual";
  el flujo de reembolso queda fuera de alcance.
- **Llamada a la pasarela dentro de la transacción.** `PaymentProcessor` llama a la pasarela con la
  transacción de BD abierta. Con la pasarela simulada no importa. Con una real, convendría separar el cobro
  de la transacción; la clave de idempotencia del pago (`enrollment-<id>`) ya evita cobrar dos veces si se
  reintenta.
- **Filas `FAILED` del outbox.** Requieren intervención manual. Las señala la métrica
  `courses_outbox_events{status="failed"}`, pero no hay un endpoint ni un proceso para republicarlas.

## Estado actual / pendiente

- [x] Esqueleto Spring Boot 4 + Testcontainers (Postgres, RabbitMQ)
- [x] Modelo de dominio y migración Flyway inicial (`V1__init.sql`)
- [x] Topología RabbitMQ (exchanges/colas/bindings, retry + DLQ) y relay del outbox
- [x] Casos de uso de inscripción/pago/certificado con reserva atómica de plazas
- [x] Tests de integración: concurrencia sobre el aforo, flujo completo por RabbitMQ, idempotencia de
      consumidores y de `Idempotency-Key`, mensaje envenenado a la DLQ
- [x] Endpoints REST con validación, paginación y OpenAPI; manejo de errores centralizado (ProblemDetail)
- [x] Tests HTTP end-to-end, de errores y de ausencia de N+1
- [x] Seguridad JWT por rol, con reglas de propiedad por recurso y tests de 401/403
- [x] Tests unitarios de dominio y de casos de uso con dobles de prueba (Mockito)
- [x] `Dockerfile` multi-stage y `docker-compose.yml` con healthchecks y secretos por entorno
- [x] Actuator en un puerto de gestión interno: health con BD/RabbitMQ y `/actuator/prometheus` para scraping
- [x] Observabilidad: métricas de negocio (inscripciones, pagos, certificados, DLQ, outbox), readiness con
      BD/RabbitMQ y logs JSON con `correlationId` propagado a través de RabbitMQ
