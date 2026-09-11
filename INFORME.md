# Informe – Lab P2: BluePrints API con JWT
**Arquitecturas de Software – ECI**  
**Nicolás Parrado – Juan Hernández**

---

## 1. Qué hicimos

Tomamos la API del laboratorio anterior (P1) y le metimos seguridad con JWT. La idea es que ahora ningún endpoint funciona sin un token válido. Para conseguir el token hay que hacer login en `/auth/login`, y ese token tiene un tiempo de vida configurable.

Usamos Spring Boot 3 con OAuth2 Resource Server, las llaves RSA se generan solas al arrancar la app (no hay que configurar nada externo), y los permisos se manejan con scopes dentro del mismo token.

---

## 2. Cómo está organizado

```
src/main/java/co/edu/eci/blueprints/
  ├── api/         → el controller con los endpoints de blueprints
  ├── auth/        → el login que emite el token
  ├── config/      → configuración de Swagger
  ├── filters/     → los tres filtros del P1 (identity, redundancy, undersampling)
  ├── model/       → Blueprint y Point
  ├── persistence/ → interfaz + implementación en memoria
  ├── services/    → la lógica de negocio
  └── security/    → todo lo de JWT: llaves, usuarios, configuración
```

---

## 3. Lo que se implementó

### Seguridad

La configuración en `SecurityConfig` define qué es público y qué no:
- Público: `/auth/login`, Swagger, actuator/health
- Todo lo de `/api/**` necesita token sí o sí

Además cada endpoint tiene su `@PreAuthorize` con el scope exacto que necesita, así que no alcanza con tener un token cualquiera.

### Scopes

| Scope | Para qué sirve |
|-------|----------------|
| `blueprints.read` | consultar blueprints (GET) |
| `blueprints.write` | crear blueprints y agregar puntos (POST, PUT) |

### Usuarios de prueba

| Usuario | Contraseña |
|---------|------------|
| student | student123 |
| assistant | assistant123 |

Ambos reciben el mismo token con los dos scopes.

### Filtros (del P1)

Se activán con perfiles de Spring:

```bash
# sin filtro (default)
mvn -q -DskipTests spring-boot:run

# elimina puntos consecutivos repetidos
mvn -q -DskipTests spring-boot:run -Dspring-boot.run.profiles=redundancy

# se queda con 1 de cada 2 puntos
mvn -q -DskipTests spring-boot:run -Dspring-boot.run.profiles=undersampling
```

### Tiempo de expiración del token

En `application.yml`:
```yaml
blueprints:
  security:
    token-ttl-seconds: 3600
```

Si lo bajas a `10` el token dura 10 segundos y después la API empieza a responder 401. Útil para ver cómo se comporta la seguridad.

---

## 4. Evidencias

### App arrancando

<!-- captura de la consola con el banner de Spring Boot -->
![app-iniciada](img-01-app-iniciada.png)

### Swagger UI

<!-- captura de http://localhost:8080/swagger-ui/index.html -->
![swagger-ui](img-02-swagger-ui.png)

### Login – obtener el token

<!-- POST /auth/login con el body y la respuesta con el access_token -->
![login](img-03-login.png)

### Pegando el token en Swagger

<!-- el modal de Authorize con el token ya puesto -->
![authorize](img-04-authorize.png)

### GET /api/blueprints – lista completa

<!-- respuesta 200 con los blueprints -->
![get-all](img-05-get-all.png)

### GET /api/blueprints/{author}

<!-- blueprints de john -->
![get-author](img-06-get-author.png)

### GET /api/blueprints/{author}/{bpname}

<!-- john/house específico -->
![get-one](img-07-get-one.png)

### POST /api/blueprints – crear uno nuevo

<!-- body con author/name/points y respuesta 201 -->
![post](img-08-post.png)

### PUT – agregar un punto

<!-- respuesta 202 -->
![put-point](img-09-put-point.png)

### Sin token – 401

<!-- GET sin Authorization header -->
![sin-token](img-10-sin-token.png)

### Token expirado – 401

<!-- con token-ttl-seconds en 10 y esperando que expire -->
![token-expirado](img-11-token-expirado.png)

---

## 5. Las actividades del README

**Actividad 1** – En `SecurityConfig` los endpoints públicos van con `.permitAll()` y los protegidos con `.hasAnyAuthority(...)`. Después cada método del controller tiene su `@PreAuthorize` para afinar más.

**Actividad 2** – El JWT tiene: `iss`, `iat`, `exp`, `sub` (el username) y `scope`. Se puede pegar en [jwt.io](https://jwt.io) para ver las claims.

**Actividad 3** – Todos los endpoints del P1 quedaron integrados y protegidos con el scope que corresponde.

**Actividad 4** – Con `token-ttl-seconds: 10` el token expira rápido y se puede ver el 401 en acción.

**Actividad 5** – Todos los endpoints tienen `@Operation`, `@ApiResponses` y `@Tag`. El esquema JWT está en `OpenApiConfig` y se referencia con `@SecurityRequirement` en el controller.
