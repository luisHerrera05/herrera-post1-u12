# Post-contenido — Unidad 12: Despliegue y CI/CD

![CI Status](https://github.com/luisHerrera05/herrera-post1-u12/actions/workflows/ci.yml/badge.svg)

## Descripción
Repositorio del laboratorio de la Unidad 12 de Programación Web — Séptimo Semestre (UDES).
Contiene una aplicación Spring Boot (API REST de productos con JPA) dockerizada con un
Dockerfile multi-stage y perfiles dev/prod (Parte 1), extendida con un pipeline de
integración continua con GitHub Actions y monitoreo con Spring Boot Actuator (Parte 2).

## Estructura
```
├── Dockerfile                  # multi-stage: Maven (build) + JRE Alpine (run)
├── .dockerignore
├── docker-compose.yml          # opcional: perfil prod con PostgreSQL local
├── pom.xml
├── src/main/resources/         # application.properties, -dev, -prod
├── src/test/java/              # pruebas (contexto + controlador + actuator)
├── .github/workflows/ci.yml    # pipeline CI
└── capturas/                   # evidencias
```

## Parte 1 — Dockerización con Perfiles y Variables de Entorno
El Dockerfile multi-stage compila el proyecto con Maven en una primera etapa y ejecuta el JAR
resultante en una etapa `eclipse-temurin:21-jre-alpine` con un usuario no root. La
configuración sensible se externaliza en `application.properties` con placeholders
`${VAR:default}`: sin variables de entorno la aplicación arranca con el perfil `dev`
(H2 en memoria); con `SPRING_PROFILES_ACTIVE=prod` y las variables `DATASOURCE_URL`,
`DATASOURCE_USER`, `DATASOURCE_PASSWORD` (y `JWT_SECRET` si aplica) arranca con la
configuración de producción.

### Variables de entorno
| Variable | Perfil | Por defecto | Descripción |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ambos | `dev` | Perfil activo |
| `PORT` | ambos | `8080` | Puerto HTTP (Railway/Render lo inyectan) |
| `DATASOURCE_URL` | ambos | H2 (dev) / PostgreSQL local (prod) | URL JDBC |
| `DATASOURCE_USER` | ambos | `sa` / `appuser` | Usuario de BD |
| `DATASOURCE_PASSWORD` | ambos | vacío | Contraseña de BD |
| `JWT_SECRET` | ambos | clave de desarrollo (dev) / vacío (prod) | Secreto JWT (si aplica) |
| `JPA_DDL_AUTO` | prod | `validate` | Usar `update` en el primer despliegue con BD vacía |

## Parte 2 — Pipeline CI/CD con GitHub Actions y Monitoreo con Actuator
El pipeline (`.github/workflows/ci.yml`) se activa en cada push o pull request a `main`:

1. **`build-and-test`**: checkout, JDK 21 (Temurin) con caché de Maven, `mvn clean verify`
   y publicación del reporte de Surefire como artefacto.
2. **`docker-build`** (`needs: build-and-test`): construye la imagen con el mismo Dockerfile,
   levanta el contenedor dentro del runner y hace *polling* (hasta 15 intentos, 2 s) a
   `/actuator/health` hasta obtener `"status":"UP"`. Si falla, imprime `docker logs`.
   Siempre limpia el contenedor (`if: always()`). No publica la imagen en ningún registro.

## Cómo compilar y ejecutar
```bash
# 1. Clonar
git clone https://github.com/luisHerrera05/herrera-post1-u12.git && cd herrera-post1-u12

# 2. Construir la imagen
docker build -t mi-spring-app:local .

# 3. Perfil dev (valores por defecto, H2 en memoria)
docker run --rm -p 8080:8080 mi-spring-app:local

# 4. Perfil prod (requiere una PostgreSQL accesible)
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/appdb \
  -e DATASOURCE_USER=appuser -e DATASOURCE_PASSWORD=apppass \
  -e JWT_SECRET=una-clave-larga-solo-para-produccion \
  -e JPA_DDL_AUTO=update \
  mi-spring-app:local

# 4b. Alternativa: prod + PostgreSQL con docker compose
docker compose up --build

# 5. Verificar
curl http://localhost:8080/actuator/health      # {"status":"UP",...}
curl http://localhost:8080/api/productos
```

## Despliegue (paso final manual)
Desplegado en Railway/Render a partir del mismo Dockerfile, con las variables configuradas en el panel:
- URL de la aplicación: `https://REEMPLAZAR.up.railway.app`
- Health: `https://REEMPLAZAR.up.railway.app/actuator/health`

Pasos: (1) iniciar sesión con GitHub; (2) *Deploy from GitHub repo* y autorizar el repositorio;
(3) la plataforma detecta el Dockerfile y construye la imagen; (4) agregar un servicio PostgreSQL
y definir `SPRING_PROFILES_ACTIVE=prod`, `DATASOURCE_URL`, `DATASOURCE_USER`,
`DATASOURCE_PASSWORD`, `JWT_SECRET` y `JPA_DDL_AUTO=update`; (5) generar el dominio público y
comprobar `/actuator/health`.

## Decisiones de diseño

### Parte 1
1. **Placeholders con valor por defecto (`${VAR:default}`) en vez de estrictos.** Un placeholder
   estricto obliga a exportar todas las variables incluso en local y en CI y falla si falta una.
   Con defaults seguros de desarrollo (H2, clave de desarrollo) el mismo artefacto arranca sin
   fricción, y en producción cada valor se sobrescribe. No se aplica a credenciales de producción:
   `JWT_SECRET` y la contraseña de BD quedan vacíos en `prod`.
2. **`application-dev.properties` y `application-prod.properties` separados** en vez de bloques
   condicionales en un solo archivo: la separación física hace evidente qué configuración
   corresponde a cada ambiente y reduce el riesgo de editar producción por error.
3. **Imagen final `eclipse-temurin:21-jre-alpine` en vez de JDK completo o distroless:** reduce el
   tamaño (sin compilador ni herramientas de build) y conserva un shell mínimo para depurar con
   `docker exec`, algo valioso en un contexto de aprendizaje.
4. **Imagen `maven:3.9-eclipse-temurin-21-alpine` para la etapa de compilación:** las imágenes
   `eclipse-temurin` no incluyen Maven; usar la imagen oficial de Maven evita instalarlo a mano.

### Parte 2
1. **Prueba mínima de contexto (más pruebas del controlador) en vez de omitir las pruebas:** un job
   sin pruebas "pasa" trivialmente; obligar a Spring a construir el contexto valida algo real
   antes de pasar al job de Docker.
2. **`docker-build` construye y verifica la imagen pero no la publica:** publicar exige Secrets de
   una cuenta externa solo para aprobar el laboratorio. El smoke test valida lo importante
   (la imagen arranca y expone el monitoreo) sin credenciales; Railway/Render construyen desde el
   mismo Dockerfile.
3. **Exposición de Actuator diferenciada por perfil** (todo en dev; solo `health` e `info` en prod):
   evita exponer información sensible (beans, entorno, métricas) públicamente.
4. **Caché de Maven (`cache: maven`) y `needs:` entre jobs:** ahorra tiempo en cada ejecución y
   evita construir la imagen si las pruebas fallan.

## Capturas de pantalla
![Imagen Docker construida localmente](capturas/docker-images.png)
![Contenedor respondiendo UP en local](capturas/actuator-health-local.png)
![GitHub Actions con checks verdes](capturas/github-actions.png)
![Smoke test respondiendo UP en el pipeline](capturas/smoke-test-log.png)
![Panel de Railway o Render con el despliegue activo](capturas/railway-panel.png)
![Endpoint /actuator/health respondiendo en la URL pública](capturas/railway-health.png)
