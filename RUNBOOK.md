# RUNBOOK — Backend Académico (U12, reto)

Guía reproducible para entregar el sistema desde una carpeta limpia, sin depender de
IntelliJ ni de su cliente HTTP. Todo se ejecuta con PowerShell + `curl.exe` (el curl real
de Windows, no el alias de `Invoke-WebRequest`) o con `Invoke-RestMethod`.

Convención: cuando un comando deba ejecutarse dentro de PowerShell, aparece con el
prompt `PS>`. No lo escribas al copiar.

---

## 0. Requisitos de JDK

El `pom.xml` fija el bytecode objetivo en Java 21 (`<java.version>21</java.version>` o
equivalente en `maven.compiler.release`), pero el JDK que usas para *compilar* puede ser
más nuevo: un JDK 25 compila perfectamente hacia el *release* 21 gracias a
`--release 21`, que es lo que usa Maven cuando declaras `maven.compiler.release=21`.

- Si tienes JDK 25 instalado y tu `JAVA_HOME` apunta ahí, no necesitas nada más: Maven
  compilará el proyecto en modo "release 21", es decir, generará bytecode compatible con
  Java 21 aunque el compilador sea más nuevo.
- Confirma qué JDK ve Maven antes de construir:

```
PS> .\mvnw.cmd -version
```

Debe mostrar algo como `Java version: 25...` en "Java version" y, en la salida del
build más abajo, ningún error de "release version not supported".

---

## 1. Entrega desde una carpeta limpia

Simula exactamente lo que haría un revisor que nunca ha abierto IntelliJ:

```
PS> cd C:\temp
PS> git clone <url-de-tu-repositorio> entrega-u12
PS> cd entrega-u12
PS> git checkout reto-u12
```

(Sustituye `<url-de-tu-repositorio>` por el remoto real; si vas a entregar un zip en
vez de un repositorio, simplemente descomprímelo en una carpeta nueva y entra a ella.)

Verifica que no haya artefactos de compilación ya presentes:

```
PS> Get-ChildItem target -ErrorAction SilentlyContinue
```

No debe existir `target/` todavía. Si existe, bórralo para que el build que sigue sea
limpio de verdad:

```
PS> Remove-Item -Recurse -Force target -ErrorAction SilentlyContinue
```

---

## 2. Construcción y pruebas

Un solo comando construye y corre toda la suite de pruebas (unitarias + slice +
`@SpringBootTest` con Testcontainers, si tu proyecto los usa):

```
PS> .\mvnw.cmd clean verify
```

Al final debe aparecer `BUILD SUCCESS` y un resumen como:

```
Tests run: NN, Failures: 0, Errors: 0, Skipped: 0
```

Si solo quieres construir el jar sin repetir las pruebas (porque ya las corriste y
confirmaste que pasan), usa:

```
PS> .\mvnw.cmd -DskipTests clean package
```

Este segundo comando es el que necesitas antes de reconstruir la imagen Docker, porque
Docker no recompila el código Java por su cuenta: siempre que cambies algo, repite este
paso y luego el build de la imagen.

---

## 3. Inicio del sistema

Levanta Postgres y la aplicación contenida en Docker:

```
PS> docker compose up -d --build
```

Confirma que ambos contenedores estén arriba:

```
PS> docker compose ps
```

Espera a que el *health check* de la app esté en estado `healthy` (puede tardar unos
segundos mientras Flyway aplica las migraciones):

```
PS> docker compose logs -f app
```

Busca una línea como `Started BackendAcademicoApplication` y después
`Ctrl+C` para salir del *tail* de logs (no detiene el contenedor).

Confirma que el endpoint de salud responde:

```
PS> curl.exe -i http://localhost:8080/actuator/health
```

Debe regresar `200` con `"status":"UP"`.

---

## 4. Peticiones de ejemplo

### 4.0 Token CSRF (necesario para todo POST/PUT/DELETE)

`http.csrf(withDefaults())` en tu `SecurityConfig` deja la protección CSRF activa con
su repositorio por defecto, ligado a la sesión HTTP. Tu propio `CsrfController`
(`GET /api/v1/csrf`) expone el nombre del encabezado y el valor del token, y los deja
guardados en la sesión de esa respuesta — por eso hay que conservar la cookie de
sesión (`JSESSIONID`) entre esa petición y la escritura que sigue. Sin esto, cualquier
`POST`/`PUT`/`DELETE` recibe `403 Forbidden`, con rol y credenciales correctos, como ya
lo confirma tu prueba `SeguridadTest.docenteSinCsrfRecibe403`.

Patrón a repetir antes de cada bloque de peticiones de escritura (usa un archivo de
cookies por usuario, para no mezclar sesiones):

```
PS> $csrf = curl.exe -s -c docente.cookies -b docente.cookies -u docente:docente123 `
    http://localhost:8080/api/v1/csrf | ConvertFrom-Json
PS> $csrf
```

Esto debe mostrar algo como `headerName : X-XSRF-TOKEN` y `token : <valor>`. El mismo
token sirve para varias peticiones de escritura mientras reutilices el mismo archivo de
cookies y no cambies de usuario (el repositorio por defecto no rota el token en cada
uso).

**Importante sobre el cuerpo JSON:** no uses `-d '{"clave":"valor"}'` directo en la
línea de comandos. Windows PowerShell tiene un bug conocido al reconstruir argumentos
para un ejecutable nativo (`curl.exe`): si el argumento contiene comillas dobles *y*
además contiene un espacio (como `"Grace Hopper"`), PowerShell se come las comillas
dobles internas al pasarlas al proceso — el `Content-Length` que recibas será más corto
de lo esperado y Jackson fallará con `"Failed to read request"`. La manera confiable es
escribir el cuerpo a un archivo y mandarlo con `--data-binary @archivo.json`:

```
PS> Set-Content -NoNewline -Encoding utf8 alumno900.json '{"matricula":"A900","nombre":"Grace Hopper","correo":"grace@u.mx"}'
```

### 4.1 Éxito — 201 Created

```
PS> curl.exe -i -b docente.cookies -u docente:docente123 `
    -H "Content-Type: application/json" -H "$($csrf.headerName): $($csrf.token)" `
    --data-binary "@alumno900.json" `
    http://localhost:8080/api/v1/alumnos
```

Resultado esperado: `HTTP/1.1 201 Created`, encabezado `Location:
/api/v1/alumnos/<id>` y el cuerpo con el alumno creado. Anota el `<id>` que te
regrese; lo usarás en los pasos siguientes.

### 4.2 Validación — 400 Bad Request

Mismo endpoint, pero con un correo que no cumple `@Email` (mismo token, misma cookie):

```
PS> Set-Content -NoNewline -Encoding utf8 alumno901.json '{"matricula":"A901","nombre":"X","correo":"no-es-un-correo"}'
PS> curl.exe -i -b docente.cookies -u docente:docente123 `
    -H "Content-Type: application/json" -H "$($csrf.headerName): $($csrf.token)" `
    --data-binary "@alumno901.json" `
    http://localhost:8080/api/v1/alumnos
```

Resultado esperado: `HTTP/1.1 400 Bad Request`, cuerpo `application/problem+json` con
`"codigo":"DATOS_INVALIDOS"` y un arreglo `campos` señalando qué campos fallaron
(`correo` por formato, `nombre` por tamaño mínimo). Nota: este 400 sí requirió pasar
primero la barrera CSRF (si el token faltara, verías 403, no 400 — la validación de
Bean Validation ocurre dentro del controlador, después de la cadena de filtros).

### 4.3 Ausencia — 404 Not Found

Es una lectura (`GET`), así que no necesita token CSRF. Usa un id que casi seguramente
no existe:

```
PS> curl.exe -i -u docente:docente123 http://localhost:8080/api/v1/alumnos/999999
```

Resultado esperado: `HTTP/1.1 404 Not Found`, `"codigo":"ALUMNO_NO_ENCONTRADO"`.

### 4.4 Conflicto — 409 Conflict

Intenta crear otra vez una matrícula que ya existe (usa la que creaste en 4.1; mismo
token, misma cookie):

```
PS> Set-Content -NoNewline -Encoding utf8 alumno900-dup.json '{"matricula":"A900","nombre":"Otro Nombre","correo":"otro@u.mx"}'
PS> curl.exe -i -b docente.cookies -u docente:docente123 `
    -H "Content-Type: application/json" -H "$($csrf.headerName): $($csrf.token)" `
    --data-binary "@alumno900-dup.json" `
    http://localhost:8080/api/v1/alumnos
```

Resultado esperado: `HTTP/1.1 409 Conflict`, `"codigo":"MATRICULA_DUPLICADA"`.

### 4.5 Dos rechazos de seguridad de causas distintas

**Causa 1 — sin credenciales (401, producido en la cadena de filtros, antes de llegar
a cualquier controlador):**

```
PS> curl.exe -i http://localhost:8080/api/v1/alumnos
```

Resultado esperado: `HTTP/1.1 401 Unauthorized`. Nota que el cuerpo NO tiene el formato
`application/problem+json` de tu `GlobalExceptionHandler`: este rechazo ocurre antes del
`DispatcherServlet`, así que nunca pasa por tu manejador de excepciones.

**Causa 2 — autorización por objeto negada (403, producido dentro del dispatch de MVC,
por `@PreAuthorize`, y éste sí pasa por tu `GlobalExceptionHandler`):**

```
PS> curl.exe -i -u alumno:alumno123 http://localhost:8080/api/v1/alumnos/<id-de-4.1>
```

Donde `<id-de-4.1>` es el id del alumno `A900` creado en 4.1. El usuario `alumno` no es
dueño de ese expediente (su matrícula de prueba no coincide), así que el resultado
esperado es `HTTP/1.1 403 Forbidden` con `"codigo":"ACCESO_DENEGADO"` — este sí lleva
el formato `problem+json`, porque pasó por `accesoDenegado(...)` en
`GlobalExceptionHandler`.

La diferencia entre estos dos 4xx es justamente la línea que separa seguridad de
cadena de filtros (401/403 "genéricos", antes del controlador) de seguridad de método
(`@PreAuthorize`, dentro del controlador): solo el segundo tiene la oportunidad de
devolver un cuerpo de error con tu formato.

### 4.6 Mejora de acceso por propietario

Importante: la cuenta `A001` tiene rol `ALUMNO`, y tu cadena de filtros exige
`DOCENTE`/`ADMIN` para crear alumnos (`.requestMatchers("/api/v1/**")
.hasAnyRole("DOCENTE", "ADMIN")`), así que **el expediente se crea con `docente`**,
igual que en 4.1 — un alumno nunca crea su propio registro. La cuenta `A001` solo se
usa para las lecturas (`GET`) que siguen, donde sí aplica la regla de propietario.

```
PS> Set-Content -NoNewline -Encoding utf8 alumnoA001.json '{"matricula":"A001","nombre":"Ada Lovelace","correo":"ada@u.mx"}'
PS> curl.exe -i -b docente.cookies -u docente:docente123 `
    -H "Content-Type: application/json" -H "$($csrf.headerName): $($csrf.token)" `
    --data-binary "@alumnoA001.json" `
    http://localhost:8080/api/v1/alumnos
```

Anota el id regresado (llamémoslo `<id-A001>`). Las tres consultas que siguen son
`GET`, así que no necesitan token CSRF, y es ahí donde entra en juego la cuenta
`A001`.

- **Propietario consulta su propio expediente (200):**
  ```
  PS> curl.exe -i -u A001:a001123 http://localhost:8080/api/v1/alumnos/<id-A001>
  ```
  Resultado esperado: `200 OK`.

- **Usuario ajeno consulta el expediente de otro (403):**
  ```
  PS> curl.exe -i -u alumno:alumno123 http://localhost:8080/api/v1/alumnos/<id-A001>
  ```
  Resultado esperado: `403 Forbidden`, `"codigo":"ACCESO_DENEGADO"`.

- **Administrador consulta cualquier expediente sin ser el dueño (200):**
  ```
  PS> curl.exe -i -u admin:admin123 http://localhost:8080/api/v1/alumnos/<id-A001>
  ```
  Resultado esperado: `200 OK`.

Estos tres casos demuestran la regla completa: `hasAnyRole('DOCENTE','ADMIN') or
@accesoAlumnos.esPropietario(#id, authentication)`.

---

## 5. Falla controlada de PostgreSQL: readiness vs. liveness

Detén solo el contenedor de la base de datos, dejando la app corriendo:

```
PS> docker compose stop postgres
```

Consulta los tres indicadores de salud por separado:

```
PS> curl.exe -i http://localhost:8080/actuator/health/liveness
PS> curl.exe -i http://localhost:8080/actuator/health/readiness
PS> curl.exe -i http://localhost:8080/actuator/health
```

Resultado esperado:

- **Liveness** sigue en `200 UP`. La JVM del proceso sigue viva y respondiendo; no hay
  ningún deadlock ni estado interno roto que justifique reiniciar el contenedor. Liveness
  responde la pregunta "¿debe reiniciarse este proceso?", y la respuesta es no: el
  proceso en sí está bien, lo que falla es una dependencia externa.
- **Readiness** cae a `503 OUT_OF_SERVICE` (o similar), porque el grupo de salud
  `readiness` incluye el indicador `db`, y ese indicador falla al no poder conectar con
  Postgres. Readiness responde "¿debe este contenedor seguir recibiendo tráfico?", y la
  respuesta ahora es no: en un entorno con balanceador de carga o Kubernetes, esto
  sacaría al pod de la lista de endpoints activos sin matar el proceso.
- **Health general** normalmente refleja el peor estado agregado, así que también
  muestra `DOWN`/`503`.

Intenta crear un alumno mientras Postgres está caído, para ver el efecto práctico (el
token CSRF se valida en la cadena de filtros, antes de tocar la base de datos, así que
reutiliza el `docente.cookies`/`$csrf` obtenidos en 4.0 — si ya expiró la sesión,
repite ese paso primero):

```
PS> Set-Content -NoNewline -Encoding utf8 alumno902.json '{"matricula":"A902","nombre":"Prueba","correo":"prueba@u.mx"}'
PS> curl.exe -i -b docente.cookies -u docente:docente123 `
    -H "Content-Type: application/json" -H "$($csrf.headerName): $($csrf.token)" `
    --data-binary "@alumno902.json" `
    http://localhost:8080/api/v1/alumnos
```

Esperado: un error 500 (o 503, según cómo esté configurado tu pool de conexiones) al
intentar usar la base de datos — la app sigue viva, pero no puede completar operaciones
que dependen de Postgres.

Restaura el servicio:

```
PS> docker compose start postgres
```

Espera unos segundos y vuelve a consultar readiness hasta que regrese a `200 UP`:

```
PS> curl.exe -i http://localhost:8080/actuator/health/readiness
```

La conclusión operativa: liveness protege contra procesos colgados (reinicia solo
cuando el proceso mismo está roto); readiness protege al tráfico de llegar a una
instancia que, aunque viva, no puede atender peticiones por una dependencia caída. Son
preguntas distintas y separarlas evita reinicios innecesarios cuando el problema real
está en otro servicio.

---

## 6. Decisión argumentada sobre la caché

El proyecto usa la abstracción `@Cacheable`/`@CacheEvict` de Spring con
`ConcurrentMapCacheManager` (confirmado en el `pom.xml`: solo está
`spring-boot-starter-cache`, sin Caffeine ni Redis como dependencia adicional).

**Decisión: mantener `ConcurrentMapCacheManager` para este laboratorio académico de una
sola instancia, documentando explícitamente sus límites para producción.**

Razones a favor de mantenerlo aquí:
- Cero configuración y cero dependencias nuevas; el proyecto es de una sola instancia
  (un único contenedor `app`), así que no hay problema de coherencia entre réplicas.
- El volumen de datos y de peticiones de un entorno de práctica no justifica la
  complejidad operativa de levantar Redis.

Limitaciones que lo hacen inadecuado para producción, y que cualquier evolución futura
del proyecto debería resolver:
- **Sin TTL (tiempo de vida):** una entrada cacheada nunca expira por sí sola; solo se
  elimina cuando el código ejecuta explícitamente `@CacheEvict`. Si un dato cambia por
  una vía que no pasa por ese método (una migración manual, una corrección directa en
  la base), la caché queda desincronizada indefinidamente.
- **Sin límite de tamaño:** `ConcurrentHashMap` detrás de esta implementación crece sin
  cota. En producción, con más entidades y más tráfico, esto es una fuga de memoria
  potencial.
- **Sin coordinación entre réplicas:** si el servicio escalara a más de una instancia
  (necesario para tolerancia a fallos o más capacidad), cada instancia tendría su propia
  caché en memoria. Un `@CacheEvict` ejecutado en la instancia A no invalida la copia
  cacheada en la instancia B, lo que produce lecturas inconsistentes entre réplicas.
- **Sin métricas automáticas:** se confirmó operativamente en la comprobación de
  resultado que los indicadores `cache.gets`/`cache.puts`/etc. de Actuator no se
  registran para este tipo de caché, porque el decorador transaccional
  (`TransactionAwareCacheManagerProxy`) que envuelve la caché real no es reconocido por
  los proveedores de métricas (`CacheMeterBinderProvider`) que Spring Boot trae
  integrados para tipos conocidos como la caché plana de Caffeine. Esto no es un error
  de configuración: es una limitación arquitectónica de esta combinación específica.

Alternativa concreta si el proyecto creciera más allá del laboratorio: **Caffeine**
(biblioteca en memoria con TTL y límite de tamaño configurables, ideal si se sigue
operando una sola instancia) o **Redis** (caché externa compartida, necesaria en cuanto
haya más de una réplica de la aplicación). La elección entre ambas depende
exclusivamente de si el despliegue objetivo es de una instancia o de varias: Caffeine
no resuelve el problema de coherencia entre réplicas, solo mejora el control de memoria
y expiración dentro de una sola instancia.

---

## 7. Persistencia de datos entre reinicios del contenedor

Para demostrar que los datos sobreviven a un ciclo completo de destrucción y
recreación de contenedores (siempre que no se borren los volúmenes con `-v`):

```
PS> docker compose down
PS> docker volume ls
PS> docker compose up -d
PS> curl.exe -i -u docente:docente123 http://localhost:8080/api/v1/alumnos
```

El volumen nombrado de Postgres debe seguir apareciendo en `docker volume ls`, y el
listado de alumnos debe incluir los registros creados en los pasos anteriores (`A900`,
`A001`, etc.), confirmando que los datos persisten fuera del ciclo de vida de los
contenedores.

---

## 8. Cierre

```
PS> docker compose down
```

Esto detiene y elimina los contenedores conservando el volumen de datos (sin `-v`). Si
en algún momento necesitas partir de cero por completo, incluyendo los datos:

```
PS> docker compose down -v
```

---

## Resumen de resultados esperados

| Caso | Comando (sección) | Código esperado |
|---|---|---|
| Éxito | 4.1 | 201 |
| Validación | 4.2 | 400 |
| Ausencia | 4.3 | 404 |
| Conflicto | 4.4 | 409 |
| Rechazo de seguridad #1 (sin credenciales) | 4.5 | 401 |
| Rechazo de seguridad #2 (autorización por objeto) | 4.5 | 403 |
| Propietario consulta lo propio | 4.6 | 200 |
| Ajeno consulta lo de otro | 4.6 | 403 |
| Administrador consulta cualquiera | 4.6 | 200 |
| Liveness con Postgres caído | 5 | 200 (UP) |
| Readiness con Postgres caído | 5 | 503 (DOWN) |