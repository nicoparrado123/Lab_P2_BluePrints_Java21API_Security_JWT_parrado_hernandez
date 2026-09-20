# Informe – Lab P2: BluePrints API con seguridad JWT
**Arquitecturas de Software (ARSW) – Escuela Colombiana de Ingeniería Julio Garavito**
**Nicolás Parrado – Juan Hernández**

---

## 1. Resumen

Partimos de la API de BluePrints del laboratorio P1 y la convertimos en un **OAuth 2.0 Resource Server**: ningún endpoint de negocio responde sin un **JWT firmado con RS256**, y cada operación exige un **scope** específico. El token se obtiene en el endpoint didáctico `POST /auth/login`.

Además integramos todo lo construido en el P1: rutas versionadas (`/api/v1`), respuestas uniformes `ApiResponse<T>`, manejo global de errores, filtros por perfil y persistencia opcional en PostgreSQL.

**Stack:** Java 21 · Spring Boot 3.3.2 · Spring Security 6 (Resource Server) · Nimbus JOSE + JWT · springdoc-openapi 2.6 · Spring Data JPA / PostgreSQL 16 · JUnit 5 + MockMvc.

---

## 2. Estructura

```
src/main/java/co/edu/eci/blueprints/
  ├── api/
  │    ├── BlueprintController.java        endpoints /api/v1/blueprints (con @PreAuthorize)
  │    ├── ApiResponse.java                contrato {code, message, data}
  │    └── GlobalExceptionHandler.java     400 / 404 / 409 en formato ApiResponse
  ├── auth/AuthController.java             POST /auth/login -> emite el JWT
  ├── config/OpenApiConfig.java            Swagger + esquema bearer-jwt
  ├── filters/                             Identity (default), Redundancy, Undersampling
  ├── model/                               Blueprint, Point
  ├── persistence/
  │    ├── InMemoryBlueprintPersistence    perfil por defecto
  │    └── impl/PostgresBlueprintPersistence (+ entidad JPA y repositorio)  perfil postgres
  ├── services/BlueprintsServices.java
  └── security/
       ├── SecurityConfig.java             reglas de acceso, JwtEncoder/JwtDecoder
       ├── MethodSecurityConfig.java       @EnableMethodSecurity
       ├── JwtKeyProvider.java             par de llaves RSA 2048
       ├── RsaKeyProperties.java           issuer, TTL y tolerancia de reloj
       ├── InMemoryUserService.java        usuarios de prueba y sus scopes
       ├── JsonAuthenticationEntryPoint    respuesta 401
       └── JsonAccessDeniedHandler         respuesta 403
src/main/resources/
  ├── application.yml                      configuración base (en memoria)
  ├── application-postgres.yml             perfil postgres
  └── data.sql                             datos de ejemplo para postgres
src/test/java/co/edu/eci/blueprints/       31 pruebas (ver sección 7)
```

---

## 3. Flujo de autenticación

```
Cliente                         AuthController / SecurityConfig                 BlueprintController
  │  POST /auth/login {user,pass}        │                                              │
  │ ───────────────────────────────────► │ valida contraseña (BCrypt)                   │
  │                                      │ firma JWT RS256 con la llave privada         │
  │ ◄─────────────────────────────────── │ {access_token, token_type, expires_in, scope}│
  │                                                                                     │
  │  GET /api/v1/blueprints   Authorization: Bearer <token>                             │
  │ ───────────────────────────────────► │ BearerTokenAuthenticationFilter              │
  │                                      │  1. firma con la llave pública               │
  │                                      │  2. exp (tolerancia 0 s)                     │
  │                                      │  3. iss == https://decsis-eci/blueprints     │
  │                                      │  4. scope -> authorities SCOPE_*             │
  │                                      │ regla por URL + método (hasAuthority)        │
  │                                      │ ───────────────────────────────────────────► │ @PreAuthorize
  │ ◄─────────────────────────────────────────────────────────────────────────────────── │ 200 ApiResponse
```

Si el token falta o no pasa las validaciones 1–3 la respuesta es **401**; si es válido pero no tiene el scope requerido, **403**.

---

## 4. Seguridad implementada

### 4.1 Endpoints públicos y protegidos (`SecurityConfig`)

| Ruta | Método | Acceso |
|------|--------|--------|
| `/auth/login` | POST | público |
| `/actuator/health`, `/error` | * | público |
| `/v3/api-docs/**`, `/swagger-ui/**` | * | público |
| `/api/**` | GET | `SCOPE_blueprints.read` |
| `/api/**` | POST, PUT, DELETE | `SCOPE_blueprints.write` |
| cualquier otra | * | autenticado |

La autorización se aplica en **dos capas**: la regla por URL y método de `SecurityConfig`, y `@PreAuthorize` en cada método de `BlueprintController`. Si alguien agrega un endpoint y olvida una de las dos, la otra sigue protegiendo.

### 4.2 Usuarios y scopes

| Usuario | Contraseña | Scopes en el token |
|---------|------------|--------------------|
| `student` | `student123` | `blueprints.read` |
| `assistant` | `assistant123` | `blueprints.read blueprints.write` |

Las contraseñas se guardan como hash **BCrypt**. Cuando el usuario no existe igual se ejecuta una comparación contra un hash falso, para que el tiempo de respuesta no revele qué usuarios existen.

### 4.3 Emisión y validación del token

- **Algoritmo:** RS256. `JwtKeyProvider` genera un par RSA de 2048 bits al arrancar; la llave privada firma (`JwtEncoder`) y la pública valida (`JwtDecoder`).
- **Claims emitidos:** `iss`, `sub`, `iat`, `exp`, `jti` y `scope`.
- **Validaciones:** firma, expiración e issuer. Spring por defecto solo valida fechas y además con **60 s de tolerancia**; configuramos `clock-skew-seconds: 0` para que el token expire exactamente al cumplir su TTL y añadimos `JwtIssuerValidator`.
- **Sin sesión:** `SessionCreationPolicy.STATELESS`; cada petición trae su token. Por eso CSRF está deshabilitado (no hay cookies de sesión que un sitio externo pueda aprovechar).

Configuración en `application.yml`:

```yaml
blueprints:
  security:
    issuer: "https://decsis-eci/blueprints"
    token-ttl-seconds: 3600
    clock-skew-seconds: 0
```

### 4.4 Respuestas de error

Todas las respuestas, incluidos los errores de seguridad, usan el mismo contrato `ApiResponse`. En 401 y 403 también se conserva la cabecera estándar `WWW-Authenticate` (RFC 6750).

```json
// 401 – sin token, token inválido, expirado o de otro issuer
{ "code": 401, "message": "unauthorized: ...", "data": null }

// 403 – token válido sin el scope requerido
{ "code": 403, "message": "forbidden: the token does not have the required scope", "data": null }
```

| Código | Cuándo |
|--------|--------|
| 200 | consulta exitosa |
| 201 | blueprint creado |
| 202 | punto agregado |
| 400 | JSON mal formado o campos faltantes |
| 401 | sin token / token inválido / credenciales incorrectas en el login |
| 403 | scope insuficiente |
| 404 | autor o blueprint inexistente |
| 409 | el blueprint ya existe |

---

## 5. Integración con el P1

- **Endpoints:** `GET /api/v1/blueprints`, `GET /{author}`, `GET /{author}/{bpname}`, `POST /`, `PUT /{author}/{bpname}/points`, y (agregados para el cliente React del Lab P3) `PUT /{author}/{bpname}` que reemplaza todos los puntos (200; 400 si falta `points` o si `author`/`name` del cuerpo no coinciden con la URL) y `DELETE /{author}/{bpname}` (200; 404 si no existe). Ambos exigen `blueprints.write` en `SecurityConfig` y con `@PreAuthorize`, y están implementados en memoria y en PostgreSQL.
- **`ApiResponse<T>`** en todas las respuestas y **`GlobalExceptionHandler`** para 400/404/409.
- **Filtros** activables por perfil de Spring (se aplican al consultar un blueprint específico):
  - `identity` (por defecto), `redundancy` (quita puntos consecutivos repetidos), `undersampling` (conserva 1 de cada 2 puntos).
- **Persistencia:** en memoria por defecto; PostgreSQL con el perfil `postgres`. Frente al P1 se agregó una columna de orden para los puntos (los filtros dependen del orden) y el manejo de creaciones simultáneas del mismo blueprint (responde 409 en vez de 500).
- `Blueprint` usa una lista segura para hilos, porque varias peticiones pueden agregar puntos al mismo plano a la vez.

---

## 6. Ejecución

Requisitos: JDK 21 y Maven 3.9+ (Docker solo para el perfil `postgres`).

```powershell
# En memoria, sin filtro
mvn spring-boot:run

# Con filtros
mvn spring-boot:run "-Dspring-boot.run.profiles=redundancy"
mvn spring-boot:run "-Dspring-boot.run.profiles=undersampling"

# Con PostgreSQL (se puede combinar con un filtro: postgres,redundancy)
docker compose up -d
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres"

# Pruebas
mvn clean test
```

- Swagger UI: http://localhost:8080/swagger-ui/index.html → hacer login, copiar el `access_token` y pegarlo en **Authorize** (solo el token; Swagger agrega `Bearer` automáticamente).
- `api.http` contiene todas las peticiones listas para el cliente HTTP de IntelliJ.

---

## 7. Pruebas automatizadas

`mvn clean test` → **31 pruebas, 0 fallos**.

| Clase | Pruebas | Qué cubre |
|-------|---------|-----------|
| `BlueprintsServicesTest` | 11 | servicios, persistencia en memoria, filtros |
| `SecurityIntegrationTest` | 19 | login (200/400/401), claims del JWT, 401 (sin token, malformado, firma alterada, expirado, otro issuer), 403 (student escribiendo, token sin scopes), 200/201/202/400/404/409 con tokens reales, rutas públicas |
| `TokenExpirationTest` | 1 | con TTL de 3 s el mismo token pasa de 200 a 401 al vencer |

---

## 8. Actividades del README

**Actividad 1 – Endpoints públicos y protegidos.** En `SecurityConfig.filterChain` los endpoints públicos se declaran con `.permitAll()` (login, health, Swagger) y los protegidos con `.hasAuthority(...)` según el método HTTP; lo que no coincide cae en `.anyRequest().authenticated()`. `oauth2ResourceServer().jwt()` activa el filtro que lee el header `Authorization: Bearer`. Ver tabla 4.1.

**Actividad 2 – Flujo de login y claims.** El login valida las credenciales, arma un `JwtClaimsSet` y lo firma con la llave privada. El token decodificado contiene:

| Claim | Ejemplo | Significado |
|-------|---------|-------------|
| `iss` | `https://decsis-eci/blueprints` | quién emitió el token (se valida) |
| `sub` | `student` | usuario autenticado |
| `iat` | `1758060000` | fecha de emisión (epoch s) |
| `exp` | `1758063600` | fecha de expiración = iat + TTL |
| `jti` | `3f1c...` | identificador único del token |
| `scope` | `blueprints.read` | permisos; Spring los convierte en authorities `SCOPE_*` |

Evidencia en la captura de jwt.io (sección 9) y en la prueba `tokenContainsExpectedClaims`.

**Actividad 3 – Scopes sobre los endpoints del P1.** Todos los endpoints del P1 quedaron protegidos: lecturas con `blueprints.read` y escrituras con `blueprints.write`. Para que la diferencia sea observable, `student` solo recibe lectura y `assistant` recibe ambos; `student` obtiene 403 al intentar crear o modificar.

**Actividad 4 – Tiempo de expiración.** Con `token-ttl-seconds: 10` el token deja de funcionar a los 10 s y la API responde 401 con el mensaje `Jwt expired at ...`. Hallazgo: con la configuración por defecto de Spring el token seguía siendo aceptado unos 60 s más por la tolerancia de reloj (`JwtTimestampValidator`); por eso la dejamos configurable y en 0. `TokenExpirationTest` lo verifica automáticamente.

**Actividad 5 – Documentación en Swagger.** `OpenApiConfig` define el esquema `bearer-jwt` (HTTP bearer, formato JWT) y la tabla de usuarios. Todos los endpoints tienen `@Tag`, `@Operation` y `@ApiResponses` con sus códigos (incluidos 401/403); `/auth/login` usa `@SecurityRequirements` vacío para indicar que no requiere token.

---

## 9. Evidencias

### 9.1 Pruebas automatizadas (`mvn clean test`)
![tests](docs/evidencias/img-01-tests.png)

### 9.2 Aplicación iniciada
![app-iniciada](docs/evidencias/img-02-app-iniciada.png)

### 9.3 Swagger UI
![swagger-ui](docs/evidencias/img-03-swagger.png)

### 9.4 Login de assistant (token con los dos scopes)
![login-body](docs/evidencias/img-04-login-assistant-body.png)
![login-response](docs/evidencias/img-04-login-assistant-response.png)

### 9.5 Claims del JWT decodificado
![jwt-claims](docs/evidencias/img-05-jwt-claims.png)

### 9.6 Token en Authorize
![authorize](docs/evidencias/img-06-authorize.png)

### 9.7 GET /api/v1/blueprints – 200
![get-all-request](docs/evidencias/img-07-get-all-request.png)
![get-all-response](docs/evidencias/img-07-get-all-response.png)

### 9.8 GET /api/v1/blueprints/{author} – 200
![get-author-request](docs/evidencias/img-08-get-author-request.png)
![get-author-response](docs/evidencias/img-08-get-author-response.png)

### 9.9 GET /api/v1/blueprints/{author}/{bpname} – 200
![get-one-request](docs/evidencias/img-09-get-one-request.png)
![get-one-response](docs/evidencias/img-09-get-one-response.png)

### 9.10 POST con assistant – 201
![post-request](docs/evidencias/img-10-post-201-request.png)
![post-response](docs/evidencias/img-10-post-201-response.png)

### 9.11 PUT agregar punto – 202
![put-request](docs/evidencias/img-11-put-202-request.png)
![put-response](docs/evidencias/img-11-put-202-response.png)

### 9.12 Login de student (token solo de lectura)
![login-student-request](docs/evidencias/img-12-login-student-request.png)
![login-student-response](docs/evidencias/img-12-login-student-response.png)
![authorize-student](docs/evidencias/img-13-auth-student.png)

### 9.13 POST con student – 403 (scope insuficiente)
![student-403-request](docs/evidencias/img-14-student-403-request.png)
![student-403-response](docs/evidencias/img-14-student-403-response.png)

### 9.14 Sin token – 401
![sin-token](docs/evidencias/img-15-sin-token-401.png)

### 9.15 Token expirado – 401
![token-expirado-request](docs/evidencias/img-16-token-expirado-401-request.png)
![token-expirado-response](docs/evidencias/img-16-token-expirado-401-response.png)

### 9.16 Perfil postgres: escritura desde la API y datos en la base
![postgres-put](docs/evidencias/img-17-postgres-put.png)
![postgres-select](docs/evidencias/img-18-postgres-select.png)

---

## 10. Decisiones y limitaciones

- **Login didáctico:** en un sistema real la emisión de tokens la haría un servidor de autorización (Keycloak, Azure AD, Auth0) y esta API solo validaría tokens mediante su JWKS.
- **Llaves en memoria:** se regeneran en cada arranque, así que los tokens emitidos antes de reiniciar dejan de ser válidos. En producción se cargarían desde un keystore o gestor de secretos.
- **Usuarios en memoria** y sin refresh tokens ni revocación: suficiente para el alcance del laboratorio.
