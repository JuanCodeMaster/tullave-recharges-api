# tuLlave - API de Recargas Digitales

API REST en Spring Boot para registrar, consultar y eliminar recargas digitales de la tarjeta tuLlave.
Corre con Docker junto a PostgreSQL, tiene validaciones, manejo centralizado de errores, pruebas unitarias
y de integración, y documentación con Swagger UI.

| | |
|---|---|
| Java | 21 (Eclipse Temurin) |
| Spring Boot | 3.5.6 |
| Base de datos | PostgreSQL 16 (Flyway para el esquema) |
| Build | Maven (wrapper incluido, no hace falta instalarlo) |
| Documentación | Swagger UI en `http://localhost:8080/swagger-ui.html` |

## Tabla de contenido

1. [Cómo ejecutar](#1-cómo-ejecutar)
2. [Endpoints](#2-endpoints)
3. [Cómo ejecutar las pruebas](#3-cómo-ejecutar-las-pruebas)
4. [Arquitectura y decisiones técnicas](#4-arquitectura-y-decisiones-técnicas)
5. [Dockerfile y docker-compose](#5-dockerfile-y-docker-compose)
6. [Flujo de Git](#6-flujo-de-git)
7. [Mejoras futuras](#7-mejoras-futuras)

---

## 1. Cómo ejecutar

### Con Docker (recomendado, no requiere Java en el host)

```bash
docker compose up --build
```

La primera vez tarda unos minutos porque compila el proyecto dentro de la imagen. Cuando el log muestre
`Started RechargesApiApplication`, la API queda disponible en `http://localhost:8080`:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Para detener y conservar los datos: `docker compose down`. Para borrar también la base: `docker compose down -v`.

Las credenciales por defecto (usuario, contraseña y base `tullave`) pueden cambiarse copiando `.env.example`
a `.env`.

### En local (desarrollo)

Se necesita Java 21 y un PostgreSQL. Lo más cómodo es levantar solo la base con Docker:

```bash
docker compose up -d db
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

La conexión se configura con variables de entorno (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`); por defecto apunta a
`jdbc:postgresql://localhost:5432/tullave` con usuario y contraseña `tullave`.

## 2. Endpoints

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `POST` | `/api/v1/recharges` | Registrar una recarga | `201 Created` + header `Location`; `400` si los datos no son válidos |
| `GET` | `/api/v1/getRecharges` | Listar recargas paginadas (`page`, `size`) con filtro opcional `cardNumber` | `200 OK`; `400` si los parámetros no son válidos |
| `GET` | `/api/v1/recharges` | Alias REST del listado anterior (misma lógica) | `200 OK` |
| `GET` | `/api/v1/recharges/{id}` | Consultar una recarga | `200 OK`; `404` si no existe |
| `DELETE` | `/api/v1/recharges/{id}` | Eliminar una recarga | `204 No Content`; `404` si no existe |

### Reglas de validación

| Campo | Regla |
|---|---|
| `cardNumber` | Obligatorio. Exactamente 16 dígitos numéricos. |
| `amount` | Obligatorio. Entre 2.000 y 200.000 (inclusive). Máximo 2 decimales. |
| `paymentMethod` | Obligatorio. `PSE`, `NEQUI`, `DAVIPLATA` o `CREDIT_CARD`. |
| `page` / `size` | `page >= 0`, `1 <= size <= 100`. Por defecto `page=0`, `size=10`. |

### Ejemplo

```http
POST /api/v1/recharges
Content-Type: application/json

{
  "cardNumber": "1010000012345678",
  "amount": 50000,
  "paymentMethod": "NEQUI"
}
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/recharges/1

{
  "id": 1,
  "reference": "cbf57b4d-0f23-4d07-b18a-33f5e1502d4c",
  "cardNumber": "1010000012345678",
  "amount": 50000.00,
  "paymentMethod": "NEQUI",
  "createdAt": "2026-10-04T15:30:00.123456Z"
}
```

El listado devuelve una página con metadatos propios (no el `Page` de Spring Data):

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "first": true,
  "last": false
}
```

### Formato de error

Todos los errores salen con la misma estructura. `errors` solo aparece cuando hay detalle por campo.

```json
{
  "timestamp": "2026-10-04T15:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Los datos enviados no son válidos",
  "path": "/api/v1/recharges",
  "errors": [
    { "field": "amount", "message": "El monto mínimo de recarga es 2.000" },
    { "field": "cardNumber", "message": "El número de tarjeta debe tener exactamente 16 dígitos numéricos" }
  ]
}
```

### Colección de Postman

En `postman/` está la colección **tuLlave Recargas API** con casos exitosos y de error de cada endpoint, más un
entorno local. Cada request incluye pruebas automáticas, así que puede ejecutarse completa con el Collection
Runner o desde consola:

```bash
npx newman run postman/tuLlave-Recargas-API.postman_collection.json -e postman/tuLlave-Local.postman_environment.json
```

Las carpetas están numeradas para correrse en orden: el POST exitoso guarda el id creado y lo reutilizan el GET
por id y el DELETE.

## 3. Cómo ejecutar las pruebas

```bash
./mvnw test              # unitarias (servicio con Mockito) + capa web (MockMvc)
./mvnw verify -Pit       # además, integración de extremo a extremo con PostgreSQL real (Testcontainers; requiere Docker)
```

| Clase | Tipo | Qué cubre |
|---|---|---|
| `RechargeServiceImplTest` | Unitaria (JUnit 5 + Mockito) | Lógica del servicio: creación, consulta, filtro, eliminación, enmascarado de tarjeta en logs |
| `RechargeControllerTest` | Web slice (`@WebMvcTest`) | Rutas, códigos HTTP, validación de body y query params, formato de error para 400/404/405/500 |
| `RechargeApiIT` | Integración (`@SpringBootTest` + Testcontainers) | Flujo completo contra PostgreSQL 16: migraciones Flyway, persistencia, paginación y filtro |

Los tests de integración se separan con el sufijo `IT` y el perfil `it` para que `./mvnw test` siga siendo rápido
y no dependa de Docker. El pipeline de CI (`.github/workflows/ci.yml`) ejecuta ambos niveles y construye la imagen.

## 4. Arquitectura y decisiones técnicas

### Estructura por capas

```
com.tullave.recharges
├── controller   RechargeController        Capa HTTP: rutas, códigos de estado, validación de entrada
├── service      RechargeService(+Impl)    Casos de uso y transacciones
├── repository   RechargeRepository        Acceso a datos (Spring Data JPA)
├── model        Recharge, PaymentMethod   Entidad JPA y enum
├── dto          RechargeRequest/Response, PageResponse, ErrorResponse
├── mapper       RechargeMapper            Conversión DTO <-> entidad
├── exception    GlobalExceptionHandler, ResourceNotFoundException
└── config       OpenApiConfig
```

Cada capa solo conoce a la inmediatamente inferior y el controlador depende de la interfaz del servicio, no de
la implementación. Los DTOs son `record`s inmutables; la entidad nunca sale del servicio.

### Decisiones y su justificación

| Decisión | Por qué |
|---|---|
| **Flyway gobierna el esquema** (`ddl-auto: validate`) | Dejar que Hibernate cree tablas en producción es frágil y no versionable. Con Flyway cada cambio de esquema es un script revisable, y `validate` garantiza al arrancar que entidad y tabla coinciden. |
| **Constraints también en la base de datos** (`CHECK` para tarjeta, monto y medio de pago) | La API valida con Bean Validation, pero la base es la última línea de defensa frente a scripts u otros clientes. Dos capas de validación, misma regla. |
| **`reference` (UUID) además del `id`** | El id secuencial es interno y predecible. La referencia sirve como comprobante para el usuario y para conciliar con la pasarela de pago sin exponer el tamaño de la tabla. |
| **`createdAt` como `Instant` en UTC** (`TIMESTAMPTZ`) | Evita ambigüedades de zona horaria entre contenedor, base y clientes. La conversión a hora local es responsabilidad del cliente. |
| **`BigDecimal` normalizado a 2 decimales** | Dinero nunca en `double`. Se fija la escala al construir la entidad para que la respuesta sea idéntica recién creada y leída desde la BD. |
| **Enum persistido como `STRING`** | `ORDINAL` se rompe al reordenar o insertar valores. Con texto los datos históricos siguen siendo legibles. |
| **`PageResponse` propio** en vez de `Page<T>` de Spring Data | La serialización de `PageImpl` no es estable entre versiones (Spring lo advierte en el log) y expone campos internos. Un contrato propio y pequeño es más fácil de consumir y de versionar. |
| **Tamaño máximo de página 100** | Evita que un cliente pida `size=1000000` y tumbe la base o la memoria del servicio. |
| **`/getRecharges` + alias `/recharges`** | La ruta `getRecharges` es la exigida en la especificación y se respeta. Se añade `/recharges` porque es la forma REST convencional; ambas comparten el mismo handler, sin duplicar lógica. |
| **`GET /recharges/{id}`** | No estaba en la especificación, pero el header `Location` del `201` debe apuntar a un recurso consultable. Es el complemento natural de crear y eliminar. |
| **Manejo de errores centralizado** (`@RestControllerAdvice`) | Un solo formato (`ErrorResponse`) para validaciones, enum inválido, JSON mal formado, tipos incorrectos, 404, 405 y 500. Los errores del cliente se registran en `WARN` y los inesperados en `ERROR` con stack trace, sin filtrar detalles internos al cliente. |
| **Enmascarado del número de tarjeta en los logs** | El número de tarjeta es dato personal. En logs solo se escriben los últimos 4 dígitos. |
| **`@Transactional(readOnly = true)` por defecto** en el servicio | Las lecturas no abren transacciones de escritura; solo `create` y `delete` la sobrescriben. `open-in-view` desactivado para que las sesiones de Hibernate no vivan durante todo el request. |
| **Mapper manual** | Con dos DTOs, MapStruct añade un procesador de anotaciones sin aportar valor. Si el modelo crece, el `RechargeMapper` es el único punto a migrar. |
| **Sin Lombok** | Records para los DTOs y getters explícitos en la única entidad. Menos magia en tiempo de compilación y menos dependencias. |
| **Actuator con probes de liveness/readiness** | Los usa el `HEALTHCHECK` del contenedor y servirían tal cual en Kubernetes. |
| **Springdoc / Swagger UI** | Documentación generada a partir del código y las anotaciones de validación; siempre sincronizada con lo que la API realmente hace. |

### Logs

SLF4J con Logback (el proveedor por defecto de Spring Boot):

- `INFO`: creación y eliminación de recargas (con id y referencia, tarjeta enmascarada).
- `DEBUG`: consultas de listado con sus parámetros (`APP_LOG_LEVEL=DEBUG` para activarlo).
- `WARN`: errores del cliente (validación, 404, 405) con método, ruta y detalle.
- `ERROR`: errores no controlados, con stack trace.

## 5. Dockerfile y docker-compose

### Dockerfile (multi-stage)

1. **Etapa `build`** (`eclipse-temurin:21-jdk-alpine`): copia primero `pom.xml` y el wrapper y descarga las
   dependencias (`dependency:go-offline`). Esa capa se cachea mientras no cambie el `pom.xml`, así que un cambio en
   el código no vuelve a descargar nada. Luego copia `src/` y empaqueta con `-DskipTests` (las pruebas corren en el
   pipeline, no en cada build de imagen). Finalmente extrae el jar en capas con `jarmode=tools`.
2. **Etapa `runtime`** (`eclipse-temurin:21-jre-alpine`): solo el JRE, imagen más liviana y menor superficie de
   ataque. Copia las capas en orden de menor a mayor frecuencia de cambio (dependencias, loader, snapshots,
   aplicación) para que un redeploy solo transfiera la capa de la aplicación. Corre con un usuario sin privilegios,
   expone el 8080, define `JAVA_OPTS` con `MaxRAMPercentage` para respetar el límite de memoria del contenedor y
   declara un `HEALTHCHECK` contra el endpoint de readiness.

### docker-compose.yml

- **`db`**: `postgres:16-alpine` con volumen con nombre (`tullave-pgdata`) para que los datos sobrevivan a
  `docker compose down`, y `healthcheck` con `pg_isready`.
- **`api`**: se construye desde el `Dockerfile`, recibe la conexión por variables de entorno (`DB_URL` apunta al
  servicio `db` por la red interna de Compose) y usa `depends_on` con `condition: service_healthy`: la API solo
  arranca cuando PostgreSQL acepta conexiones, así Flyway no falla en el primer intento.
- Credenciales y puerto se pueden sobrescribir con un `.env` (ver `.env.example`); el `.env` real está en
  `.gitignore`.

## 6. Flujo de Git

Se usó un flujo de ramas por funcionalidad sobre `main`, con Conventional Commits y Pull Requests:

```
main
 ├── feature/recharge-api   entidad, migración, DTOs, servicio, controlador, manejo de errores, tests
 ├── feature/docker         Dockerfile, docker-compose, .dockerignore
 └── feature/docs           README, colección Postman, CI
```

- **`main`** siempre queda en estado ejecutable; solo recibe cambios por Pull Request.
- **Ramas `feature/*`** por funcionalidad, cortas y con un objetivo claro.
- **Commits** con Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `build:`, `ci:`) y mensaje en
  imperativo que explica el *qué* y, cuando aplica, el *por qué*.
- **Pull Requests** con descripción de los cambios, cómo probarlos y qué decisiones se tomaron. En un equipo, el
  PR es el punto de revisión de código y donde corre el pipeline de CI antes de integrar.

En un equipo real este flujo se complementaría con protección de `main` (revisión obligatoria y CI en verde),
`squash` o `rebase` al integrar para mantener el historial lineal y legible, y etiquetas (`v1.0.0`) para los
despliegues.

## 7. Mejoras futuras

- **Idempotencia** en el `POST` con un header `Idempotency-Key` para evitar recargas duplicadas por reintentos del
  cliente.
- **Seguridad**: autenticación con JWT/OAuth2 y rate limiting por tarjeta o por cliente.
- **Estado de la recarga** (`PENDING`, `APPROVED`, `REJECTED`) e integración real con la pasarela de pago, con
  confirmación asíncrona.
- **Borrado lógico** en lugar de físico si se requiere auditoría contable.
- **Observabilidad**: métricas con Micrometer/Prometheus, trazas distribuidas y logs en JSON con `correlationId`.
- **Caché** del listado por tarjeta si el volumen de lectura lo justifica.
