# Courses — plataforma de cursos online orientada a eventos

[![CI](https://github.com/dreyes17/senior-backend-test/actions/workflows/ci.yml/badge.svg)](https://github.com/dreyes17/senior-backend-test/actions/workflows/ci.yml)

Backend para la prueba técnica de Senior Backend Engineer: catálogo de cursos, inscripciones con aforo
limitado, pagos con confirmación asíncrona y emisión de certificados por eventos, comunicados mediante
RabbitMQ.

Stack: Java 21 (virtual threads) · Spring Boot 4.1 · PostgreSQL · Flyway · Spring AMQP · Spring Security (JWT) ·
Caffeine · MapStruct · Bucket4j + Redis · Spring AI (servidor MCP) · Testcontainers · OpenTelemetry ·
Prometheus · Jaeger · Grafana · GitHub Actions.

## Arrancar el proyecto

### Con Docker Compose (solo necesita Docker)

```bash
cp .env.example .env          # secretos de evaluación local; .env está fuera de git
docker compose up --build     # PostgreSQL + RabbitMQ + Redis + aplicación + Prometheus, Jaeger y Grafana
```

- La aplicación queda en <http://localhost:8080>. La documentación interactiva está en
  <http://localhost:8080/swagger-ui.html> y la especificación OpenAPI en `/v3/api-docs`.
- La interfaz de RabbitMQ está en <http://localhost:15672>, con las credenciales de `.env`. Sirve para ver
  las colas y las DLQ.
- Prometheus está en <http://localhost:9090>, solo accesible desde tu máquina. Recoge las métricas de la
  aplicación y evalúa las alertas (ver [Prometheus y alertas](#prometheus-y-alertas)).
- Grafana está en <http://localhost:3000> con el dashboard de la plataforma ya cargado, y Jaeger en
  <http://localhost:16686> con las trazas de cada petición. Los dos solo son accesibles desde tu máquina (ver
  [Trazas distribuidas](#trazas-distribuidas-opentelemetry--jaeger) y [Dashboard](#dashboard-grafana)).
- El servidor MCP está en `http://localhost:8080/mcp` (ver [Integración MCP](#integración-mcp)).
- Hay una cuenta ADMIN creada con `ADMIN_EMAIL`/`ADMIN_PASSWORD`. Ver [Seguridad](#seguridad) para obtener un
  token (dentro del devcontainer, lee antes la nota siguiente).
- `docker compose down -v` lo para todo y borra los volúmenes de datos.

> **Si lanzas `docker compose` desde dentro del devcontainer:** ese entorno usa su propio Docker
> (Docker-in-Docker), así que los puertos quedan publicados en el `localhost` del contenedor, no en el de tu
> máquina. Para abrir la API desde tu navegador, tu IDE o herramienta de devcontainers tiene que reenviar a tu
> máquina el puerto `8080` del contenedor. `devcontainer.json` lo declara en `forwardPorts`, pero no todos los
> IDE aplican esa configuración automáticamente. Si `http://localhost:8080` no responde, reenvía el puerto
> `8080` del contenedor con el mecanismo de tu IDE y usa la dirección local que te asigne, que puede no ser el
> 8080 si ese puerto ya está ocupado en tu máquina. Para la interfaz de RabbitMQ de este stack, haz lo mismo
> con el `15672`, y para Prometheus, Grafana y Jaeger con el `9090`, el `3000` y el `16686`. Ojo: el `15672`
> de tu máquina puede estar apuntando ya a la RabbitMQ del propio devcontainer, que es otra instancia con otras
> credenciales.
>
> **Las variables del devcontainer mandan sobre `.env`.** Docker Compose da prioridad a las variables del
> shell sobre las de `.env`, y el devcontainer ya exporta `JWT_SECRET`, `ADMIN_EMAIL` y `ADMIN_PASSWORD` con
> sus valores de desarrollo (ver [`.devcontainer/README.md`](.devcontainer/README.md)). Así que, lanzado
> desde dentro del devcontainer, el ADMIN del stack de Compose usa la contraseña del devcontainer, no la de tu
> `.env`. Los ejemplos de este README toman `$ADMIN_EMAIL`/`$ADMIN_PASSWORD` del shell, así que dentro del
> devcontainer funcionan tal cual. Si prefieres los valores de `.env`, arranca con
> `env -u JWT_SECRET -u ADMIN_EMAIL -u ADMIN_PASSWORD docker compose up --build`.
>
> Si ejecutas `docker compose` directamente en tu máquina, fuera del devcontainer, no hace falta nada de esto.

Cómo se comporta el despliegue:

- **Secretos:** solo llegan por el entorno o por `.env`. Si falta alguno obligatorio, `docker compose` se
  detiene y dice cuál.
- **Orden de arranque:** la aplicación espera a que Postgres y RabbitMQ pasen su healthcheck. Su propio
  healthcheck consulta `/actuator/health/readiness` en el puerto de gestión (ver [Actuator](#actuator-y-puerto-de-gestión)).
- **Puertos:** se publican el 8080 (la API y el servidor MCP) y el 15672 (interfaz de RabbitMQ), y solo en
  `127.0.0.1` el 9090 (Prometheus), el 3000 (Grafana) y el 16686 (Jaeger). Actuator escucha en el 8081,
  que solo es accesible dentro de la red de Docker.
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
- **Servidores MCP para asistentes de IA:** documentación actualizada de las librerías; inspección de la BD,
  del broker y de los buckets de rate limiting en Redis; consultas a Prometheus y a las trazas de Jaeger; y
  GitHub. Detalle en [`.devcontainer/README.md`](.devcontainer/README.md).

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
las transacciones, devuelven vistas `record`, nunca entidades), `web` los controladores REST,
`messaging` los listeners de RabbitMQ y `mcp` las tools del servidor MCP. Controladores, listeners y tools son
adaptadores finos: validan o leen la entrada, delegan en `application` y traducen el resultado.

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
| `shared` | `BaseEntity`, jerarquía de excepciones de dominio, `GlobalExceptionHandler` (errores RFC 9457), `PageResponse`, `MappingConfig` (MapStruct), `CacheConfig` (caché del catálogo), OpenAPI y `shared.security` (filtros, JWT, reglas de acceso) |

### Separación de responsabilidades (punto 6 del enunciado)

| Requisito | Dónde | Cómo se cumple |
|---|---|---|
| Controladores REST finos | `<contexto>.web` (`CourseController`, `EnrollmentController`...) | Validan la entrada con Jakarta Validation, aplican `@PreAuthorize`, delegan en `application` y traducen a HTTP (`201` + `Location`, `204`...). No contienen reglas de negocio. |
| Servicios / casos de uso | `<contexto>.application` (`EnrollmentService`, `CourseService`, `PaymentProcessor`...) | Coordinan el caso de uso y son dueños de las transacciones (`@Transactional`). Las reglas de estado viven en las entidades. |
| Repositorios | `<contexto>.repository` | Spring Data JPA, más las consultas que requieren cuidado: el `UPDATE` atómico de plazas, `@EntityGraph` contra el N+1 y `Specification` para la búsqueda. |
| Entidades de dominio separadas de los DTOs | Entidades en `<contexto>.domain`; DTOs de entrada como `record` en `web` (`CatalogRequests`, `EnrollRequest`...); DTOs de salida como vistas `record` en `application` (`CourseView`, `EnrollmentView`...) | Ningún endpoint recibe ni devuelve una entidad JPA. Las vistas las construyen mappers generados por MapStruct (ver [Mapeo entidad → vista](#mapeo-entidad--vista-mapstruct)). |
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
  AMQP y delegan. Así se añadió el servidor MCP: `catalog.mcp` y `enrollment.mcp` son otro adaptador de
  entrada sobre los mismos casos de uso, sin cambiar ninguno.
- **El contrato de eventos** (`messaging.events`) es independiente de las entidades, así que el formato
  publicado no cambia al refactorizar el modelo interno.

Si el proyecto creciera, la migración a hexagonal sería incremental: extraer interfaces de repositorio
hacia `application` contexto a contexto, sin rehacer lo demás.

Las entidades son modelos ricos: los cambios de estado válidos viven como métodos en la propia entidad
(`Course.publish()`, `Enrollment.cancel()`, `Payment.confirm()`, ...) y lanzan una excepción de dominio
específica ante una transición inválida, en vez de exponer setters y dejar la validación a quien la llame.

### Mapeo entidad → vista (MapStruct)

Cada contexto tiene un mapper, `CatalogViewMapper` y `EnrollmentViewMapper`, que convierte las entidades en
las vistas `record` que devuelven los casos de uso. MapStruct genera su implementación al compilar: es código
Java normal, sin reflexión en ejecución, y se puede leer en `target/generated-sources/annotations`.

- **Solo en un sentido: entidad → vista.** Las entidades se crean y se modifican con sus métodos de dominio
  (`Course.draft(...)`, `course.updateDetails(...)`), que protegen sus invariantes. Un mapper DTO → entidad
  se las saltaría rellenando campos directamente, así que no existe.
- **Un campo sin origen no compila.** `MappingConfig` fija `unmappedTargetPolicy = ERROR`: si se añade un
  campo a una vista y el mapper no sabe de dónde sale, falla la compilación, en lugar de aparecer un `null`
  en la API.
- **Lo que el compilador no ve lo cubren tests:** que `categoryId` salga de la categoría y no del
  instructor, o que `availableSeats` sea `capacity - seatsTaken`. Son `CatalogViewMapperTest` y
  `EnrollmentViewMapperTest`, sin Spring.
- `availableSeats` se declara con una expresión explícita. Sin ella, MapStruct interpretaría
  `Course.hasAvailableSeats()` como una comprobación de presencia del campo (convención `hasX`). El
  resultado sería el mismo, pero por casualidad.

El esquema de base de datos vive en `src/main/resources/db/migration` (Flyway, `ddl-auto: validate`) y
replica en la propia BD las invariantes críticas: `courses` tiene `CHECK (seats_taken <= capacity)`, y
`enrollments` tiene un índice único parcial que impide que un mismo estudiante tenga dos inscripciones
`PENDING_PAYMENT`/`ACTIVE` para el mismo curso a la vez.

## API REST

Todos los endpoints cuelgan de `/api` y están documentados en Swagger UI (<http://localhost:8080/swagger-ui.html>)
y en la especificación OpenAPI (`/v3/api-docs`).

| Recurso | Operaciones | Filtros del listado |
|---|---|---|
| `/api/categories` | crear, listar, obtener, renombrar (`PUT`), `POST /{id}/archive`, `POST /{id}/activate`, borrar (409 si tiene cursos) | `name`, `status` |
| `/api/auth` | `POST /register` (alta pública de estudiante), `POST /token` (login → JWT) | — |
| `/api/instructors` | crear instructor y su cuenta (email único), listar, obtener, actualizar perfil, borrar (409 si tiene cursos) | `name`, `email` |
| `/api/courses` | crear (en `DRAFT`), buscar, buscar con cursor (`GET /scroll`), obtener, actualizar, `POST /{id}/publish`, `POST /{id}/archive`, borrar (solo `DRAFT`) | ver *Búsqueda de cursos*, abajo |
| `/api/students` | listar, obtener | `name` (nombre o apellido), `email` |
| `/api/enrollments` | inscribir (`Idempotency-Key` obligatoria; el estudiante sale del token), obtener, `PUT /{id}/progress`, `POST /{id}/cancel` | — |
| `/api/courses/{id}/enrollments` | estudiantes inscritos en un curso | `status` |
| `/api/students/{id}/enrollments` | cursos de un estudiante | `status` |

**La especificación basta para integrarse.** Cada operación documenta su código de éxito real (`201` en las
altas, `204` en los borrados) y todos sus errores, con el cuerpo `application/problem+json` (esquema
`ProblemDetail`). Los errores que se deducen de la propia operación los añade `OpenApiConfig` a todas:

| Error | Cuándo se documenta |
|---|---|
| 400 | la operación recibe cuerpo o parámetros |
| 401 | necesita token (todas salvo login y registro) |
| 403 | tiene una regla de rol o de propiedad (`@PreAuthorize`) |
| 404 | direcciona un recurso por id en la ruta |

Los que dependen de reglas de negocio (409, 422, 429, y el 404 de un id que llega en el cuerpo) se declaran
en cada endpoint con `@ApiResponse` y una descripción del caso concreto: por ejemplo, `POST /api/enrollments`
documenta 409 para *curso lleno, no publicado o estudiante ya inscrito* y 422 para *`Idempotency-Key` reutilizada
con otra petición*. `ApiDocumentationTest` comprueba que cada operación tiene exactamente una respuesta de
éxito con su cuerpo, y que todos los errores usan el esquema `ProblemDetail`.

- **Transiciones de estado como acciones.** Publicar, archivar y cancelar son `POST` sobre un subrecurso, no
  un `PUT` que cambie el campo `status`. Así la regla de negocio de cada transición vive en un único método
  del dominio.
- **Paginación, ordenación y filtrado en todos los listados.** Aceptan `page`, `size` (por defecto 20, máximo
  100) y `sort` (propiedades de la entidad, p. ej. `sort=price,desc`), además de los filtros de la tabla
  anterior. Los filtros son opcionales y se combinan entre sí; los de texto buscan una subcadena sin distinguir
  mayúsculas. Responden con `{content, page, size, totalElements, totalPages}`. Ningún endpoint devuelve una
  tabla entera. La búsqueda de cursos ofrece además paginación por cursor (ver
  [Paginación por cursor](#paginación-por-cursor)).
- **Rate limiting** en login, registro, inscripción y MCP: 429 con `Retry-After` (ver
  [Rate limiting](#rate-limiting)).
- **Búsqueda de cursos.** Todos los filtros son opcionales y combinables: `categoryId`, `level`, `minPrice`,
  `maxPrice`, `title` (subcadena sin distinguir mayúsculas) y `withAvailableSeats=true`, además de `status`.
- **Correlación.** Cualquier petición puede enviar `X-Correlation-Id` (si no, se genera uno), y la respuesta
  siempre lo devuelve. Sirve para localizar en los logs todo lo que provocó esa petición (ver
  [Observabilidad](#observabilidad)).
- **Sin N+1 en los listados relacionales.** Cursos con su categoría e instructor, estudiantes de un curso y
  cursos de un estudiante se cargan con `@EntityGraph` sobre relaciones *to-one*, así que la paginación
  sigue haciéndose en SQL. `QueryEfficiencyTest` cuenta las sentencias SQL del hilo: cada página cuesta como
  máximo 2 consultas, sea cual sea su tamaño.

### Paginación por cursor

`GET /api/courses/scroll` acepta los mismos filtros que la búsqueda y ordena de más reciente a más antiguo,
paginando por *keyset*. El `nextCursor` de cada respuesta marca el último curso devuelto. Se envía como
`cursor` para pedir la página siguiente, y es `null` en la última:

```bash
curl "localhost:8080/api/courses/scroll?size=20&level=BEGINNER" -H "Authorization: Bearer $TOKEN"
# {"content": [ ... ], "nextCursor": "MjAyNi0wOS0yOVQxMjoxMToyOS4xNDVafDFm..."}
curl "localhost:8080/api/courses/scroll?size=20&level=BEGINNER&cursor=MjAyNi0wOS0y..." -H "Authorization: Bearer $TOKEN"
```

Diferencias con la paginación por número de página, que se mantiene en `GET /api/courses`:

- **Sin duplicados ni huecos.** Con `page=1`, la BD salta las N primeras filas en el momento de la consulta.
  Si entre dos páginas se crea un curso, todo se desplaza una fila y el cliente ve un curso repetido. El
  cursor apunta a una posición concreta, `(createdAt, id)`, así que la página siguiente empieza justo después
  del último curso visto. `CursorPaginationTest` lo reproduce: crea un curso a mitad del recorrido y
  comprueba que con el cursor no se repite ni se salta nada, mientras que con `page=1` se repite un curso.
- **Coste constante.** La condición `(created_at, id) < cursor` recorre el índice
  `(status, created_at DESC, id DESC)` (`V4__courses_keyset_index.sql`), así que la página mil cuesta lo mismo
  que la primera. Con `OFFSET`, la BD tiene que recorrer y descartar todas las filas anteriores.
- **Sin consulta de conteo.** Se leen `size + 1` filas: si llega la extra, hay página siguiente. Cada página
  es una sola consulta, con categoría e instructor en el mismo `JOIN` (`QueryEfficiencyTest`).
- **Orden total.** El `id` desempata los cursos creados en el mismo instante, así que ningún curso puede
  quedar fuera por un empate.
- **Cursor opaco.** Es Base64 de `createdAt|id`. Uno manipulado o inventado devuelve 400
  (`Invalid cursor`).

Cuándo usar cada una: el cursor, para recorrer listas largas o sin fin (el scroll de una app, una
exportación); el número de página, cuando hace falta saltar a una página concreta, ordenar por otros campos
o conocer el total.

### Errores

`GlobalExceptionHandler` responde siempre `application/problem+json` (RFC 9457) con `title`, `detail`,
`status` e `instance`. Solo llegan al cliente mensajes escritos por la aplicación: cualquier excepción
inesperada se registra en el servidor y se devuelve como un 500 genérico.

| Situación | Excepción | HTTP |
|---|---|---|
| Cuerpo inválido (Bean Validation) | `MethodArgumentNotValidException` → incluye `errors` por campo | 400 |
| Cabecera obligatoria ausente, `sort` sobre una propiedad inexistente, filtro o id con un valor inválido | `MissingRequestHeaderException`, `PropertyReferenceException`, `MethodArgumentTypeMismatchException` | 400 |
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
set -a; . ./.env; set +a    # fuera del devcontainer; dentro, las variables ya están definidas
TOKEN=$(curl -s localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r .accessToken)
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
| STUDENT | Ver el catálogo (solo cursos `PUBLISHED`); inscribirse, lo que inicia el pago (ver [Flujo de inscripción](#flujo-de-inscripción)); ver, actualizar progreso y cancelar **sus** inscripciones |

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

### Rate limiting

Límites por cliente con *token bucket* (Bucket4j), guardados en **Redis** para que se cumplan entre todas las
instancias (`RateLimitFilter`, `RedisBuckets`):

| Regla | Endpoint | Límite por defecto | Cuenta por | Qué evita |
|---|---|---|---|---|
| `login` | `POST /api/auth/token` | 10 por minuto | IP | adivinar contraseñas |
| `registration` | `POST /api/auth/register` | 20 por hora | IP | alta masiva de cuentas |
| `enrollment` | `POST /api/enrollments` | 30 por minuto | usuario | un cliente reintentando en bucle |
| `mcp` | `POST /mcp` | 120 por minuto | usuario | un agente de IA en bucle; cubre también `enroll_student` |

- Al superarlo, la respuesta es **429** en `problem+json`, con `Retry-After` en segundos. Las peticiones
  permitidas llevan `X-RateLimit-Remaining`.
- **Por IP o por usuario.** Login y registro son anónimos, así que cuentan por IP. El resto cuenta por
  usuario, porque el filtro va detrás de la autenticación JWT: estudiantes detrás de la misma IP (una
  universidad, un NAT) no comparten cupo.
- **La IP es la del cliente directo.** `X-Forwarded-For` no se lee nunca, porque cualquiera puede
  falsificarla. Detrás de un proxy inverso de confianza, `server.forward-headers-strategy` hace que la IP
  sea la del cliente real.

**Por qué en Redis y no en memoria.** Con contadores en memoria, cada instancia cuenta por separado: con N
instancias, el límite efectivo es N veces el configurado, y un límite contra fuerza bruta que se multiplica
así es más débil de lo que parece. En Redis, todas las instancias comparten un único cupo por cliente, y
además sobrevive a un reinicio de la aplicación.

- **Sin carreras entre instancias.** Bucket4j actualiza cada bucket con *compare-and-swap*, así que dos
  peticiones simultáneas en instancias distintas nunca consumen el mismo token.
- **Redis no crece sin límite.** Cada clave (`rate-limit:<regla>:<ip|user>:<id>`) caduca cuando su bucket
  se habría recargado del todo. Un cliente inactivo no ocupa nada, y perder su clave no cambia nada.
- **Solo guarda esto.** Por eso no tiene volumen en Compose: perder Redis solo reinicia los contadores.

**Si Redis falla, las peticiones pasan (*fail-open*).** Es una decisión deliberada: que caiga la protección
no debe tirar la API con ella, y el login seguiría protegido por el coste de BCrypt. Para que eso no ocurra en
silencio:

- Cada comprobación tiene un *timeout* de 200 ms (`app.rate-limit.redis-timeout`). Sin conexión, las
  operaciones se rechazan al momento en lugar de encolarse, y la reconexión se reintenta como mucho cada 5
  segundos. Una caída de Redis no añade latencia apreciable.
- Cada petición que pasa sin comprobar suma en `courses_rate_limit_unavailable_total`. La alerta
  `RateLimitingUnavailable` salta en cuanto aparece, y el panel de rate limiting de Grafana la muestra.
- Redis aparece en `/actuator/health`, pero no en la sonda de *readiness*: una instancia sin Redis sigue
  pudiendo servir tráfico.
- La aplicación arranca aunque Redis no esté: la conexión se abre en el primer uso.

**Comprobado en el stack de Compose:**

- el 11.º login seguido recibe 429 y el bucket aparece en Redis;
- tras reiniciar la aplicación, el límite se mantiene;
- con Redis parado, los logins pasan, se cuentan, la instancia sigue *ready* y la alerta pasa a `firing`;
- al volver Redis, se vuelve a limitar sin reiniciar la aplicación.

Los límites se configuran en `app.rate-limit.*` y se pueden desactivar con `RATE_LIMIT_ENABLED=false`. Los
rechazos se cuentan en `courses_rate_limit_rejected_total{rule}`.

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
instancia sin BD o sin broker deja de recibir tráfico. Redis queda fuera a propósito, porque sin él solo se
degrada el rate limiting. `/actuator/health/liveness` no los incluye a
propósito. Si se cayera la BD y la sonda de *liveness* dependiera de ella, el orquestador reiniciaría
procesos que están sanos, y eso no arreglaría nada.

**Cómo se aplica.**
- La cadena de seguridad de la API también se aplicaría al puerto de gestión, así que `SecurityConfig`
  define una cadena propia para él, con prioridad.
- Esa cadena solo se aplica cuando la petición llega al puerto **real** del servidor de gestión (lo guarda
  `ManagementPort` al arrancar) **y** su ruta está bajo `/actuator`. Así funciona también con un puerto
  aleatorio, y aunque alguien configurase el mismo puerto para API y gestión, esta cadena nunca podría abrir
  la API.

**`/actuator/info`** describe qué se está ejecutando: nombre, versión y fecha de compilación (el goal
`build-info` del `spring-boot-maven-plugin`) y la versión de Java.

En Compose, el servicio `prometheus` lee `http://app:8081/actuator/prometheus` por esa red interna (ver
[Prometheus y alertas](#prometheus-y-alertas)). También se puede comprobar a mano:

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
| `courses_rate_limit_rejected_total{rule}` | contador | Peticiones rechazadas con 429, por regla (ver [Rate limiting](#rate-limiting)) |
| `cache_gets_total{cache,result}` | contador | Lecturas de cada caché del catálogo, `hit` o `miss` (ver [Caché del catálogo](#caché-del-catálogo)) |

- **Solo se cuenta lo que confirma.** Las inscripciones creadas, los pagos y los certificados se
  incrementan después del *commit*. Una operación que se revierte no infla la métrica, y un reintento
  tras un rollback no cuenta doble. Los rechazos sí se cuentan al momento: el rechazo es en sí el resultado.
- **Todas las series existen desde el arranque**, con valor 0. Un panel o una alerta no se encuentra con una
  serie que "aún no existe".
- **Los *gauges* se calculan en cada lectura de métricas:** consultan al broker (profundidad de cada DLQ) y
  a la BD (conteo por estado sobre un índice que ya existe). Si alguno no responde, el *gauge* vale `NaN` en
  lugar de romper la exportación del resto.

### Prometheus y alertas

`docker compose up` levanta también Prometheus 3.15, configurado en `observability/prometheus/`. Lee
`http://app:8081/actuator/prometheus` cada 15 segundos por la red interna y evalúa estas reglas
(`alerts.yml`):

| Alerta | Condición | Severidad | Qué significa |
|---|---|---|---|
| `CoursesInstanceDown` | `up == 0` durante 1 min | critical | Prometheus no consigue leer la aplicación |
| `MessagesInDeadLetterQueue` | `courses_messaging_dlq_messages > 0` durante 1 min | warning | Un consumidor agotó sus reintentos. Hay que revisar los mensajes y republicarlos o descartarlos |
| `OutboxEventsFailed` | `courses_outbox_events{status="failed"} > 0` | critical | Eventos que el relay abandonó; requieren intervención manual |
| `RateLimitingUnavailable` | alguna petición pasó sin comprobar en los últimos 5 min | warning | Redis no responde y el rate limiting no se está aplicando (ver [Rate limiting](#rate-limiting)) |
| `OutboxPublishingStalled` | `pending` no baja de 50 en 5 min | warning | El relay vacía el outbox cada 500 ms, así que un atasco sostenido indica que no puede publicar |

- **La interfaz queda en <http://localhost:9090> y se publica solo en `127.0.0.1`.** Muestra todas las
  métricas, que la aplicación mantiene fuera de la red pública a propósito (ver
  [Actuator](#actuator-y-puerto-de-gestión)).
- **Comprobado de extremo a extremo** con el stack de Compose: el target `app:8081` aparece `up`, llegan las
  métricas de negocio y de caché, y las cinco reglas cargan sin errores. Tras dejar un mensaje en
  `certificates.enrollment-completed.dlq`, `MessagesInDeadLetterQueue` pasó a `firing` al cumplirse el minuto.
- **El CI valida la configuración y las reglas** con `promtool` (ver [Integración continua](#integración-continua)).
- Queda fuera un Alertmanager, que decidiría a quién avisar y cómo.

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
una línea por evento y con `correlationId`, `traceId` y `spanId` como campos, listos para un recolector de
logs. En desarrollo local son texto plano, con esos ids entre corchetes. Por ejemplo:

```json
{"@timestamp":"…","log":{"level":"INFO","logger":"…PaymentProcessor"},"process":{"thread":{"name":"rabbit-simple-3"}},"correlationId":"demo-23643","message":"Payment … confirmed (transaction …)", …}
```

**El correlation-id se mantiene junto a las trazas** (ver abajo): no depende del muestreo, así que está en
todos los logs y eventos, y es lo que el cliente ve en `X-Correlation-Id`.

### Trazas distribuidas (OpenTelemetry + Jaeger)

Micrometer Tracing con el puente de OpenTelemetry. En Compose, cada petición se traza y se exporta por OTLP a
Jaeger (<http://localhost:16686>, solo desde tu máquina). Una inscripción aparece como **una sola traza**,
desde la petición HTTP hasta el último consumidor:

```
POST /api/enrollments                                  petición HTTP (con seguridad y @PreAuthorize)
└─ outbox publish EnrollmentCreated                    relay del outbox, continuando la traza de la petición
   └─ courses.events/enrollment.created send
      └─ payments.enrollment-created receive           consumidor de pagos
         └─ outbox publish PaymentConfirmed
            └─ courses.events/payment.confirmed send
               └─ enrollments.payment-confirmed receive  activación de la inscripción
```

**El problema que había que resolver** es el mismo que con el correlation-id. La propagación automática no
cruza el outbox: el evento se publica más tarde y desde el hilo del relay, cuando la petición ya terminó, así
que la traza se cortaría justo antes de RabbitMQ. Se resuelve igual que el correlation-id:

1. `OutboxRecorder` guarda junto al evento el `traceparent` W3C del span actual
   (`V5__outbox_trace_parent.sql`).
2. `OutboxRelay` publica cada evento dentro de un span que continúa esa traza (`TracePropagation`).
3. Con la observación de Spring AMQP activada, `RabbitTemplate` envía el contexto en las cabeceras del
   mensaje y cada listener lo continúa. Los eventos que emite un consumidor guardan a su vez su
   `traceparent`, y la cadena sigue.

**Comprobado.** En el stack de Compose, la traza de una inscripción reúne los 13 spans: la petición y su
seguridad, dos publicaciones del relay, dos envíos y dos consumos. `ObservabilityTest` lo comprueba sin
Jaeger: envía una inscripción con un `traceparent` conocido y verifica que el `PaymentConfirmed`, que registra
el consumidor de pagos, lleva el mismo trace id. Sin la continuación en el relay, el test falla.

- **Muestreo:** 10 % por defecto (`TRACING_SAMPLING_PROBABILITY`), para que en producción cueste poco.
  Compose lo sube al 100 %.
- **Exportación:** solo si se define `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`, como hace Compose.
  En desarrollo local y en los tests no se exporta nada.
- **Logs:** cada línea lleva `traceId` y `spanId` además del `correlationId`, así que desde un log se llega a
  su traza.

### Dashboard (Grafana)

Grafana (<http://localhost:3000>, solo desde tu máquina) arranca con Prometheus como fuente de datos y con el
dashboard **Courses — plataforma** ya provisionado desde `observability/grafana/` como página de inicio. Se
puede ver sin iniciar sesión; para editarlo hace falta la cuenta `admin` con `GRAFANA_ADMIN_PASSWORD`.

| Fila | Paneles |
|---|---|
| Estado | instancias arriba, mensajes en DLQ, eventos del outbox `FAILED` y pendientes; cambian de color cuando requieren atención |
| Tráfico HTTP | peticiones por segundo por código de respuesta, latencia p95 por endpoint, rechazos por rate limiting |
| Negocio | inscripciones por resultado, pagos confirmados y fallidos, certificados emitidos |
| Mensajería, outbox y caché | mensajes en cada DLQ, eventos del outbox por estado, tasa de aciertos de cada caché |
| Recursos | conexiones de Hikari (activas, esperando, máximo), hilos de la JVM, memoria heap |

- El p95 se calcula con los histogramas de latencia que exporta la aplicación (`percentiles-histogram` para
  `http.server.requests`), así que es correcto también al agregar varias instancias.
- El panel de Hikari cierra el razonamiento de los virtual threads: con ellos, el límite real es el pool de
  conexiones, y un valor sostenido en "esperando" indica saturación.
- Comprobado con el stack de Compose: tras generar tráfico, todas las consultas del dashboard devuelven
  datos.
- Las trazas se consultan en la interfaz de Jaeger, enlazada desde el dashboard. No están integradas en
  Grafana porque Jaeger 2 solo sirve su API de consulta v3, que el datasource de Jaeger de Grafana 13 no usa.

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

### Confirmación del pago: por qué no hay un endpoint "pagar"

El enunciado pide confirmar el pago de forma simulada (5.2.3) y que el estudiante pueda pagar (tabla de roles), y
a la vez que esa confirmación se procese de forma asíncrona por RabbitMQ, con un consumidor que procesa el pago y
publica `PaymentConfirmed` o `PaymentFailed` (7.1). Aquí se resuelve así:

- **Pagar es inscribirse.** `POST /api/enrollments` crea el `Payment` en `PENDING` junto a la inscripción, y
  `EnrollmentCreated` actúa como orden de cobro.
- **Confirmar es trabajo del consumidor.** `PaymentProcessor` cobra contra `SimulatedPaymentGateway` y publica
  el resultado. Nada en la petición HTTP decide si el pago sale bien.
- **Por qué no un endpoint en el que el estudiante confirma su propio pago:** el resultado de un cobro lo
  decide la pasarela, nunca quien paga. Un endpoint así permitiría activar una inscripción sin cobrarla. Con una
  pasarela real, su confirmación llegaría por un *webhook*, que sería otro adaptador de entrada con el mismo
  efecto que el consumidor actual: publicar `PaymentConfirmed`.
- **Cómo probar el rechazo.** La pasarela simulada aprueba cualquier importe hasta
  `app.payments.simulation.decline-above` (10 000 por defecto) y rechaza los superiores. Un curso con un precio
  mayor recorre la rama `PaymentFailed`: la inscripción se cancela y la plaza se libera.

## Concurrencia: reserva de plazas

**Estrategia elegida: actualización atómica condicional en SQL.**

```sql
UPDATE courses SET seats_taken = seats_taken + 1, version = version + 1
 WHERE id = ? AND status = 'PUBLISHED' AND seats_taken < capacity
```

Si la sentencia actualiza 0 filas, no hay plaza (o el curso no está publicado). Entonces el servicio carga el
curso y `Course.assertAcceptsEnrollment()` decide qué excepción de dominio corresponde: `CourseFullException`
si está lleno, o `InvalidStateTransitionException` si no está `PUBLISHED`. Las dos responden 409.

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
  `IdempotencyKeyReusedException` (422).
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

## Integración MCP

La aplicación es también un servidor MCP (Model Context Protocol). Un asistente de IA puede consultar el
catálogo, inscribir estudiantes o seguir una inscripción usando la lógica real de la aplicación, con los
mismos permisos que su token.

**Dependencia:** `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`, el starter oficial que pide
el enunciado, en Spring AI 2.0.1 (compatible con Spring Boot 4). Las tools son métodos anotados con
`@McpTool` y `@McpToolParam` en `catalog.mcp.CatalogTools` y `enrollment.mcp.EnrollmentTools`.

**Cómo está hecho:**

- **Es un adaptador de entrada más, como los controladores.** Cada tool valida su entrada con Jakarta
  Validation, aplica la misma regla `@PreAuthorize` que su endpoint REST y delega en el mismo caso de uso.
  No hay lógica ni datos propios del MCP.
- **Mismo token, mismos permisos.** `/mcp` está protegido por la cadena de seguridad de la API: sin
  `Authorization: Bearer` responde 401, y cada tool se ejecuta con los permisos de ese token. Por ejemplo, un
  STUDENT no puede usar `create_category` y solo ve cursos `PUBLISHED`.
- **Sin estado (`protocol: STATELESS`).** Cada llamada es una petición HTTP independiente, como en la API
  REST: cualquier instancia puede atenderla y no hay sesión MCP que guardar. Además, así la tool se ejecuta
  en el hilo de la petición, con su contexto de seguridad.
- **Errores sin detalles internos.** `McpToolErrors` es el equivalente de `GlobalExceptionHandler`: el
  cliente solo recibe mensajes escritos por la aplicación (`Course … not found`,
  `Invalid arguments: progress must be less than or equal to 100`). Cualquier error inesperado llega como
  `An unexpected error occurred` y se registra en el log.
- **Rate limiting propio.** `/mcp` tiene su propio límite por usuario, que cubre también `enroll_student`
  (ver [Rate limiting](#rate-limiting)).

**Tools disponibles.** Los parámetros marcados con `?` son opcionales. En los listados, `page` empieza en 0
y `size` va de 1 a 100 (20 por defecto).

| Tool | Qué hace | Parámetros | Quién puede usarla |
|---|---|---|---|
| `list_categories` | Lista las categorías por orden alfabético | `name?`, `status?`, `page?`, `size?` | cualquier usuario |
| `get_category` | Devuelve una categoría | `categoryId` | cualquier usuario |
| `create_category` | Crea una categoría (nombre único) | `name`, `description?` | ADMIN |
| `list_courses` | Lista los cursos, del más reciente al más antiguo | `page?`, `size?` | cualquier usuario (un STUDENT solo ve `PUBLISHED`) |
| `search_courses` | Busca cursos con filtros combinables | `categoryId?`, `level?`, `minPrice?`, `maxPrice?`, `title?`, `withAvailableSeats?`, `status?`, `page?`, `size?` | cualquier usuario (un STUDENT solo ve `PUBLISHED`) |
| `get_course` | Devuelve un curso con sus plazas libres | `courseId` | cualquier usuario (un STUDENT solo ve `PUBLISHED`) |
| `create_course` | Crea un curso en `DRAFT` | `title`, `description?`, `durationHours`, `level`, `price`, `capacity`, `categoryId`, `instructorId` | ADMIN, o el INSTRUCTOR que lo imparte |
| `publish_course` | Publica un curso `DRAFT` | `courseId` | ADMIN o el instructor del curso |
| `archive_course` | Archiva un curso: deja de aceptar inscripciones | `courseId` | ADMIN o el instructor del curso |
| `list_students` | Lista los estudiantes | `name?`, `email?`, `page?`, `size?` | ADMIN |
| `get_student` | Devuelve un estudiante | `studentId` | ADMIN o el propio estudiante |
| `enroll_student` | Inscribe al estudiante del token: reserva plaza y crea el pago, y devuelve `PENDING_PAYMENT` | `courseId`, `idempotencyKey` | STUDENT |
| `get_enrollment` | Devuelve el estado y el progreso de una inscripción | `enrollmentId` | ADMIN, su estudiante o el instructor del curso |
| `update_enrollment_progress` | Fija el progreso (0-100). Al llegar a 100 la inscripción se completa y se emite el certificado | `enrollmentId`, `progress` | ADMIN o su estudiante |
| `cancel_enrollment` | Cancela una inscripción y libera su plaza | `enrollmentId` | ADMIN o su estudiante |
| `list_students_by_course` | Lista los estudiantes inscritos en un curso | `courseId`, `status?`, `page?`, `size?` | ADMIN o el instructor del curso |
| `list_courses_by_student` | Lista los cursos de un estudiante | `studentId`, `status?`, `page?`, `size?` | ADMIN o el propio estudiante |

- `idempotencyKey` es obligatoria en `enroll_student` por la misma razón que la cabecera `Idempotency-Key`
  en REST: un agente que reintenta tras un *timeout* recibe la inscripción original en lugar de ocupar otra
  plaza.
- Cada tool declara las *hints* de MCP que le corresponden (`readOnlyHint` en las consultas,
  `idempotentHint`, `destructiveHint`). Los clientes las usan para decidir cuándo pedir confirmación.

**Cómo probarlo.** Con la aplicación arrancada (Compose o `./mvnw spring-boot:run`), obtén un token de ADMIN.
Fuera del devcontainer, carga antes las credenciales de tu `.env` con `set -a; . ./.env; set +a`; dentro, el
shell ya las tiene (ver la nota sobre las variables del devcontainer en
[Arrancar el proyecto](#arrancar-el-proyecto)):

```bash
TOKEN=$(curl -s localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r .accessToken)
```

- **MCP Inspector:** `npx @modelcontextprotocol/inspector` y abre <http://localhost:6274>. Elige el
  transporte *Streamable HTTP* con la URL `http://localhost:8080/mcp`, y añade la cabecera
  `Authorization: Bearer <token>` en la configuración de autenticación. *List Tools* muestra las 17 tools con
  sus parámetros, y cada una se puede ejecutar desde un formulario. En el devcontainer, el puerto 6274 ya se
  reenvía.
- **MCPJam:** `npx @mcpjam/inspector@latest`, con la misma URL y la misma cabecera.
- **curl:** al no tener estado, cada llamada es una petición JSON-RPC:

  ```bash
  curl -s localhost:8080/mcp -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_courses","arguments":{"title":"spring"}}}'
  ```

- **Claude Code:** `claude mcp add --transport http courses http://localhost:8080/mcp --header "Authorization: Bearer $TOKEN"`.

`McpServerTest` recorre todo esto con el cliente oficial del SDK de MCP, sobre HTTP real:

- la lista de tools;
- el flujo completo: crear categoría y curso, publicar, buscar, inscribirse, reintentar con la misma clave y
  ver la activación asíncrona por RabbitMQ;
- los permisos;
- la validación y los mensajes de error;
- el 401 sin token.

Comprobado también en el stack de Compose con `curl`: 17 tools, `search_courses` devuelve los cursos reales,
`create_category` como STUDENT responde `You are not allowed to perform this operation` y sin token la
respuesta es 401.

## Rendimiento: virtual threads y caché

### Virtual threads

Con `spring.threads.virtual.enabled=true`, tres fuentes de hilos pasan a usar virtual threads:

| Dónde | Antes | Ahora |
|---|---|---|
| Peticiones HTTP (Tomcat) | pool de 200 hilos de plataforma | un virtual thread por petición |
| Consumidores `@RabbitListener` | hilos de plataforma del contenedor de listeners | virtual threads (`rabbit-simple-N` en los logs) |
| Tareas `@Scheduled` (relay del outbox) | un hilo de plataforma | virtual threads |

**Por qué aporta aquí.** Casi todo el tiempo de una petición o de un mensaje se pasa esperando a PostgreSQL
o a RabbitMQ. Con hilos de plataforma, cada espera ocupa uno de los 200 hilos de Tomcat, y un pico de
peticiones lentas deja en cola al resto aunque la CPU esté libre. Un virtual thread bloqueado apenas ocupa
memoria y libera su hilo portador, así que el número de peticiones en curso deja de estar limitado por el
tamaño de un pool.

**Qué no cambia.** El límite real del trabajo con la BD sigue siendo el pool de Hikari (10 conexiones por
defecto): con virtual threads, las peticiones que superan ese límite esperan una conexión en lugar de esperar
un hilo. Tampoco cambia la garantía de aforo, que da el `UPDATE` condicional y no el número de hilos.

**El riesgo en Java 21: *pinning*.** En Java 21, un virtual thread que se bloquea dentro de un bloque
`synchronized` no suelta su hilo portador. Java 24 lo corrige (JEP 491). En lugar de suponerlo, lo medí: la
suite completa se ejecutó con `-Djdk.tracePinnedThreads=short`, que registra cada vez que ocurre, y no
apareció ningún caso. Esa ejecución incluye el test de concurrencia, los flujos por RabbitMQ, el relay del
outbox y el acceso JDBC. Para repetirlo:

```bash
./mvnw test -DargLine="-Djdk.tracePinnedThreads=short"   # cada caso aparecería con su traza en la salida
```

`VirtualThreadsTest` comprueba cada fuente ejecutando trabajo en ella: un consumidor creado con la misma
factoría que los `@RabbitListener` y una tarea en el scheduler del relay. Para Tomcat verifica su executor,
porque MockMvc no pasa por el servidor. Con la propiedad desactivada, los tres tests fallan.

### Caché del catálogo

Spring Cache con Caffeine (en memoria), configurado en `shared.config.CacheConfig`:

| Caché | Qué guarda | TTL | Se invalida al... |
|---|---|---|---|
| `categories` | categoría por id | 10 min | editar, archivar, activar o borrar esa categoría |
| `category-pages` | páginas del listado de categorías sin filtros (clave: `page`, `size`, `sort`); las filtradas no se cachean porque casi nunca se repiten | 10 min | cualquier cambio en categorías |
| `instructors` | instructor por id | 10 min | editar o borrar ese instructor |
| `courses` | curso por id, con sus plazas disponibles | 1 min | editar, publicar, archivar o borrar el curso; **reservar o liberar una plaza**; renombrar su categoría o su instructor |

Los TTL se configuran con `CACHE_CATALOG_TTL` y `CACHE_COURSE_TTL`.

**Qué no se cachea: la búsqueda de cursos.** Combina seis filtros con paginación y orden, así que la misma
clave casi nunca se repite. Además incluye el filtro de plazas disponibles, que cambia con cada inscripción:
cada reserva obligaría a invalidar todas las búsquedas. Mucho coste de invalidación para pocos aciertos. El
detalle de un curso sí compensa: es la consulta que más se repite, y se invalida con precisión, solo para
ese curso.

**Cómo se evita servir datos viejos:**

- **Las plazas nunca se quedan atrás.** `availableSeats` no cambia en `CourseService`, sino en el `UPDATE`
  atómico. Por eso la invalidación está en el propio repositorio: `@CacheEvict` sobre `tryReserveSeat` y
  `releaseSeat`. Cualquier camino que toque las plazas (inscribir, cancelar, pago fallido) invalida el curso,
  sin que cada servicio tenga que acordarse.
- **Invalidación después del *commit*.** El `CacheManager` es *transaction-aware*: una invalidación dentro
  de una transacción se aplica al confirmarse. Si se aplicara antes, otra petición podría volver a cachear la
  fila antigua en ese intervalo; y si hay *rollback*, no se invalida nada.
- **Los permisos no dependen de la caché.** Un curso se cachea sea cual sea su estado, pero la regla "los
  estudiantes solo ven cursos `PUBLISHED`" se comprueba en cada llamada, también en los aciertos. Por eso la
  caché vive en un bean aparte, `CourseViewCache`: la comprobación nunca se salta, y el proxy de Spring
  intercepta la llamada (no intercepta las que un bean se hace a sí mismo).
- **Nombres embebidos.** La vista de un curso incluye el nombre de su categoría y de su instructor, así que
  renombrar cualquiera de los dos vacía la caché de cursos. Es una operación de administración poco
  frecuente.

**Límite conocido: la caché es local a cada instancia.** Con varias instancias, un cambio solo invalida la
caché de la instancia que lo hizo, y las demás pueden servir el dato anterior durante, como mucho, el TTL:
1 minuto para cursos y 10 para el resto. Las reservas no se ven afectadas, porque el `UPDATE` atómico lee
siempre la BD; lo que puede quedar desfasado es la cifra de plazas que se muestra. Para este tamaño es
aceptable. Si no lo fuera, bastaría con cambiar el `CacheManager` por uno de Redis: las anotaciones y las
invalidaciones no cambian.

**Métricas.** `cache_gets_total{cache, result="hit"|"miss"}`, junto con `cache_puts_total` y
`cache_evictions_total`, en `/actuator/prometheus`: la tasa de aciertos de cada caché se ve directamente.

`CatalogCacheTest` comprueba que:

- la segunda lectura no llega a la BD;
- un cambio hecho a través de la aplicación se ve de inmediato;
- las plazas están al día justo después de inscribir y de cancelar;
- renombrar un instructor refresca sus cursos;
- un borrador que ha cacheado un ADMIN sigue oculto para los estudiantes;
- se exportan las métricas de aciertos.

## Tests

`./mvnw test` ejecuta los 137 tests en menos de un minuto. Casi todos los de integración comparten un único
contexto de Spring y un único par de contenedores (`AbstractIntegrationTest`), por eso la suite es rápida pese
a usar PostgreSQL y RabbitMQ reales. Hay otros dos contextos:

- `@RealServerTest`, con servidores reales en puertos reales. Lo comparten `ManagementPortTest`,
  `VirtualThreadsTest` y `McpServerTest`.
- El de `RateLimitingTest`, que activa el rate limiting con límites bajos. En el contexto común está
  desactivado, porque la suite hace cientos de logins desde la misma dirección.

| Nivel | Qué cubre | Clases |
|---|---|---|
| **Unitarios de dominio** | Máquinas de estado e invariantes de `Course` y `Enrollment`, sin Spring ni mocks | `CourseTest`, `EnrollmentTest` |
| **Unitarios de casos de uso** (Mockito) | Ramas de error y lo que *no* debe ocurrir: no consumir plaza si ya está inscrito, no cobrar una inscripción cancelada, no reactivar una cancelada, no emitir un segundo certificado, entregas duplicadas sin efectos, login que no revela qué emails existen | `EnrollmentServiceTest`, `PaymentProcessorTest`, `PaymentOutcomeHandlerTest`, `CertificateIssuerTest`, `IdempotentRequestsTest`, `AccountServiceTest` |
| **Unitarios de mapeo** | Que cada campo anidado o derivado de las vistas sale de su origen correcto | `CatalogViewMapperTest`, `EnrollmentViewMapperTest` |
| **Integración** (Testcontainers) | Concurrencia sobre el aforo (20 hilos, 3 plazas), flujo completo por RabbitMQ, idempotencia de consumidores con entregas duplicadas, mensaje envenenado → DLQ sin reintentos, mensaje que falla al procesarse → reintentos con backoff → DLQ, `Idempotency-Key` | `EnrollmentConcurrencyTest`, `EnrollmentFlowTest`, `ConsumerIdempotencyTest` |
| **HTTP** (MockMvc) | Flujo end-to-end por la API con cada rol, mapeo de errores a `problem+json`, 401/403 y reglas de propiedad, filtros de los listados, ausencia de N+1 (también con filtro) | `EnrollmentApiTest`, `ErrorHandlingApiTest`, `SecurityApiTest`, `ListFilteringApiTest`, `QueryEfficiencyTest` |
| **Documentación de la API** | Cada operación de la especificación OpenAPI tiene su código de éxito real y todos sus errores como `ProblemDetail` | `ApiDocumentationTest` |
| **Puertos reales** | Actuator accesible sin credenciales solo en el puerto de gestión, health y readiness con BD y broker, `info` con la compilación y Java, API protegida y ausente en ese puerto | `ManagementPortTest` |
| **Virtual threads** | Peticiones HTTP, consumidores RabbitMQ y tareas programadas se ejecutan en virtual threads | `VirtualThreadsTest` |
| **Observabilidad** | `correlationId` y traza de OpenTelemetry propagados de la petición HTTP al consumidor a través del outbox y RabbitMQ, ids no seguros sustituidos, contadores por resultado, *gauges* de DLQ y de outbox `FAILED` | `ObservabilityTest` |
| **Caché** | Lecturas servidas desde la caché, invalidación en cada escritura (plazas incluidas), permisos aplicados también en los aciertos, métricas | `CatalogCacheTest` |
| **Paginación por cursor** | Recorrido completo sin repetir ni saltar cursos, curso creado a mitad del recorrido, solo `PUBLISHED` para estudiantes, cursor inválido, tamaño máximo | `CursorPaginationTest` |
| **Rate limiting** | Login limitado por IP (429 con `Retry-After`, métrica, otra IP sin afectar), inscripción y MCP limitados por usuario y no por IP, cupo restante; con Redis real: dos instancias comparten un cupo, un Redis inaccesible falla rápido y el filtro deja pasar y cuenta la petición | `RateLimitingTest`, `RedisRateLimitingTest` |
| **MCP** | Las 17 tools con descripción y parámetros; flujo completo de inscripción a través de tools con reintento idempotente y activación asíncrona; mismos permisos que la API; validación; errores sin detalles internos; 401 sin token | `McpServerTest`, `McpToolErrorsTest` |
| **Contrato** | Campos del JSON de los eventos publicados | `EventContractTest` |

Para comprobar que los tests no pasan por casualidad, quité a propósito ocho protecciones y confirmé que los
tests fallaban:

- sin el `@EntityGraph`, `QueryEfficiencyTest` detecta el N+1;
- sin la comprobación de estado en `PaymentProcessor`, `PaymentProcessorTest` detecta que se cobra una
  inscripción cancelada;
- sin la cabecera `x-correlation-id` en `OutboxRelay`, `ObservabilityTest` detecta que el id no llega al
  consumidor;
- con `spring.threads.virtual.enabled=false`, fallan los tres tests de `VirtualThreadsTest`;
- sin el `@CacheEvict` de `tryReserveSeat`/`releaseSeat`, `CatalogCacheTest` detecta que el curso sigue
  mostrando 2 plazas libres después de una inscripción;
- sin registrar el filtro de rate limiting, fallan los tests de `RateLimitingTest`;
- sin el `@PreAuthorize` de `create_category`, `McpServerTest` detecta que un estudiante puede crear
  categorías;
- sin la continuación de la traza en el relay del outbox, `ObservabilityTest` detecta que el
  `PaymentConfirmed` acaba en otra traza.

## Integración continua

`.github/workflows/ci.yml` se ejecuta en cada push a `main`, en cada *pull request* y a mano. Tiene dos jobs en
paralelo:

| Job | Qué hace |
|---|---|
| Build and test | `./mvnw -B verify` con Java 21 (Temurin): compila y ejecuta los 137 tests. Los de integración usan el Docker que ya traen los runners `ubuntu-latest`, así que Testcontainers funciona sin configuración. Si algo falla, sube los informes de Surefire como artefacto. |
| Docker image and deployment config | Construye la imagen del `Dockerfile`, valida `docker-compose.yml` con `.env.example`, valida el JSON del dashboard de Grafana y valida la configuración y las alertas de Prometheus con `promtool`. |

- Reutiliza las dependencias de Maven entre ejecuciones (caché de `setup-java`), tiene permisos de solo
  lectura y cancela la ejecución anterior de la misma rama cuando llega un push nuevo.
- El workflow pasa `actionlint` sin avisos, y cada uno de sus pasos se ejecutó en local con el mismo comando.

## Limitaciones conocidas

- **Pago confirmado de una inscripción ya cancelada.** Si el estudiante cancela mientras el cobro está en
  curso, el pago puede confirmarse igualmente. `PaymentProcessor` reduce esa ventana: no cobra si la
  inscripción ya no está `PENDING_PAYMENT`. Si aun así ocurre, se registra un aviso de "reembolso manual";
  el flujo de reembolso queda fuera de alcance.
- **Llamada a la pasarela dentro de la transacción.** `PaymentProcessor` llama a la pasarela con la
  transacción de BD abierta. Con la pasarela simulada no importa. Con una real, convendría separar el cobro
  de la transacción; la clave de idempotencia del pago (`enrollment-<id>`) ya evita cobrar dos veces si se
  reintenta.
- **Rate limiting sin Redis.** Si Redis no responde, las peticiones pasan sin límite (*fail-open*
  deliberado) hasta que vuelve. La alerta `RateLimitingUnavailable` lo señala (ver
  [Rate limiting](#rate-limiting)).
- **Caché local a cada instancia.** Con varias instancias, la cifra de plazas mostrada puede ir hasta 1
  minuto por detrás en las que no hicieron el cambio (ver [Caché del catálogo](#caché-del-catálogo)).
- **Filas `FAILED` del outbox.** Requieren intervención manual. Las señala la métrica
  `courses_outbox_events{status="failed"}`, pero no hay un endpoint ni un proceso para republicarlas.

## Estado actual / pendiente

- [x] Esqueleto Spring Boot 4 + Testcontainers (Postgres, RabbitMQ)
- [x] Modelo de dominio y migración Flyway inicial (`V1__init.sql`)
- [x] Topología RabbitMQ (exchanges/colas/bindings, retry + DLQ) y relay del outbox
- [x] Casos de uso de inscripción/pago/certificado con reserva atómica de plazas
- [x] Tests de integración: concurrencia sobre el aforo, flujo completo por RabbitMQ, idempotencia de
      consumidores y de `Idempotency-Key`, mensaje envenenado a la DLQ
- [x] Endpoints REST con validación, paginación, filtros y OpenAPI (códigos de éxito y errores documentados);
      manejo de errores centralizado (ProblemDetail)
- [x] Tests HTTP end-to-end, de errores y de ausencia de N+1
- [x] Seguridad JWT por rol, con reglas de propiedad por recurso y tests de 401/403
- [x] Tests unitarios de dominio y de casos de uso con dobles de prueba (Mockito)
- [x] `Dockerfile` multi-stage y `docker-compose.yml` con healthchecks y secretos por entorno
- [x] Actuator en un puerto de gestión interno: health con BD/RabbitMQ y `/actuator/prometheus` para scraping
- [x] Observabilidad: métricas de negocio (inscripciones, pagos, certificados, DLQ, outbox), readiness con
      BD/RabbitMQ y logs JSON con `correlationId` propagado a través de RabbitMQ
- [x] Bonus: virtual threads (medido: sin *pinning*), Prometheus con alertas en Compose, CI con GitHub
      Actions, caché del catálogo con Caffeine y mapeo entidad → vista con MapStruct
- [x] Bonus: rate limiting (Bucket4j sobre Redis, compartido entre instancias), paginación por cursor,
      servidor MCP con Spring AI conectado a los casos de uso reales, y trazas de OpenTelemetry a través del
      outbox y RabbitMQ con Jaeger y dashboard de Grafana
