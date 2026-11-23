# Documentación de implementación — CU01: Iniciar sesión (`RF01`)

## 1. Contexto y requisitos cubiertos

- **Requisito funcional:** `RF01` — Iniciar sesión en el sistema.
- **Caso de uso:** `CU01` — Iniciar sesión.
- **Actores:** Todos los actores del sistema (`PACIENTE`, `MEDICO`, `ADMISION`, `ADMINISTRADOR`).
- **Acuerdos del proyecto aplicados:**
  - **Estructura Maven multimódulo (`D-002`):** implementación dividida en `servidor` y `cliente`.
  - **División cliente-servidor (Acta 3):** el flujo de `CU01` cubre tanto la lógica y API del servidor como la validación, comunicación y presentación del cliente.
  - **Requisitos técnicos (`RT01`–`RT05`):** arquitectura cliente-servidor sobre HTTP/REST + JSON (compatible con futuros clientes de escritorio, web y móvil) e interfaz desacoplada para la integración progresiva con el **SSO corporativo** del SESCAM.

---

## 2. Arquitectura y estructura de código

Se ha implementado `CU01` en **Java 21** sin dependencias externas de ejecución (únicamente `junit-jupiter` en ámbito `test`), siguiendo una arquitectura por capas:

### 2.1. Módulo `servidor` (`es.uclm.esi.iso2.B101.servidor`)

| Capa | Clases | Responsabilidad |
| :--- | :--- | :--- |
| **Dominio** (`dominio`) | `Rol`, `Usuario`, `Sesion`, `ResultadoAutenticacion`, `RegistroAuditoriaAcceso` | Modelos del negocio: roles de los 4 actores, estado de cuenta (activa/bloqueada, contador de intentos fallidos), sesiones con expiración y registros de auditoría de acceso (base para `RF25`). |
| **Seguridad** (`seguridad`) | `GestorContrasenas`, `GeneradorTokens` | Derivación de contraseñas con **PBKDF2WithHmacSHA256** (65.536 iteraciones, salt aleatorio de 16 bytes y comparación en tiempo constante) y generación de tokens de sesión criptográficamente seguros (`SecureRandom` + Base64 URL-safe). |
| **Persistencia** (`persistencia`) | `RepositorioUsuarios`, `RepositorioUsuariosMemoria`, `RepositorioSesiones`, `RepositorioSesionesMemoria`, `RepositorioAuditoriaAcceso`, `RepositorioAuditoriaAccesoMemoria` | Contratos de repositorio e implementaciones en memoria *thread-safe* (`ConcurrentHashMap` / `CopyOnWriteArrayList`). Incluye 4 usuarios de demostración precargados (`paciente1`, `medico1`, `admision1`, `admin1`). |
| **Integración** (`integracion`) | `ProveedorSSO`, `ProveedorSSOSimulado` | Puerto de integración con el SSO corporativo (`RT05`) e implementación simulada para desarrollo y pruebas. |
| **Servicio** (`servicio`) | `ServicioAutenticacion` | Lógica central de `CU01`: validación de credenciales, verificación de cuenta activa, bloqueo temporal tras superar el máximo de intentos fallidos, autenticación delegada vía SSO, validación de vigencia de sesión, cierre de sesión y registro de auditoría. Recibe un `Clock` inyectable para pruebas deterministas. |
| **API HTTP** (`api`) | `ServidorHTTP`, `JsonUtil`, `AplicacionServidor` | Servidor HTTP/REST basado en `com.sun.net.httpserver.HttpServer` con hilos virtuales de Java 21 y serialización JSON propia sin dependencias externas. |

#### Endpoints REST expuestos por el servidor

| Método | Ruta | Entrada | Respuestas |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | `{"username":"...","password":"..."}` | `200 OK` (token y datos de sesión), `400` (vacías), `401` (inválidas), `403` (inactiva), `423` (bloqueada) |
| `POST` | `/api/auth/sso` | `{"ssoToken":"..."}` | `200 OK`, `401` (token SSO inválido), `403` (cuenta inactiva) |
| `GET` | `/api/auth/session` | Cabecera `Authorization: Bearer <token>` | `200 OK` (sesión vigente), `401` (ausente/expirada/cerrada) |
| `POST` | `/api/auth/logout` | Cabecera `Authorization: Bearer <token>` | `200 OK`, `401` (token inválido) |

### Integración de la sesión con CU02 y CU08

El servidor HTTP comparte la instancia de `ServicioAutenticacion` y el repositorio de sesiones con los endpoints de CU02 y CU08. Sus adaptadores extraen el token Bearer, validan la sesión y obtienen el rol y `usuarioId` desde ella; no aceptan esos datos de la petición. La asociación con `pacienteId` y `medicoId` es explícita y configurable, ya que son identificadores de dominio diferentes (`USR-001` → `paciente-1`; `USR-002` → `medico-1` en la configuración demostrativa). CU08 también comprueba la relación médico-paciente antes de crear una prescripción.

La configuración demostrativa mantiene usuarios, asociaciones y datos en memoria. En un despliegue real, las asociaciones y la política clínica deben conectarse a sus repositorios y reglas persistentes.

### 2.2. Módulo `cliente` (`es.uclm.esi.iso2.B101.cliente`)

| Capa | Clases | Responsabilidad |
| :--- | :--- | :--- |
| **Modelo** (`modelo`) | `SesionCliente`, `RespuestaLogin` | DTOs inmutables (`record`) para representar la sesión activa y la respuesta de autenticación. |
| **Comunicación** (`comunicacion`) | `ClienteAutenticacion`, `JsonClienteUtil` | Cliente HTTP (`java.net.http.HttpClient`) que consume los 4 endpoints del servidor y gestiona errores de red y códigos HTTP. |
| **Presentación** (`presentacion`) | `ControladorInicioSesion`, `AplicacionCliente` | Validación previa en cliente, gestión del estado de la sesión actual e interfaz de consola interactiva multiplataforma (Windows/Linux/Mac). |

---

## 3. Corrección previa en la configuración Maven

En el `pom.xml` padre se corrigió un salto de línea erróneo en la propiedad `<project.build.sourceEncoding>` (`UTF\n8` → `UTF-8`) para garantizar la compilación correcta de caracteres en español en cualquier entorno y en GitHub Actions.

---

## 4. Pruebas automatizadas y verificación

Se han añadido 12 pruebas unitarias y de integración con **JUnit Jupiter**, ejecutadas y verificadas mediante `mvn -B verify` (el mismo comando que ejecuta el workflow de CI `.github/workflows/maven.yml`):

- **`ServicioAutenticacionTest`** (7 tests): inicio de sesión de los 4 actores, rechazo de credenciales vacías/nulas, usuario inexistente o contraseña incorrecta, bloqueo tras intentos fallidos y desbloqueo al expirar el tiempo, rechazo de cuenta inactiva, login por SSO corporativo, y expiración/cierre de sesión.
- **`ServidorHTTPTest`** (2 tests): flujo completo extremo a extremo (`login` → `session` → `logout` → rechazo posterior) y login SSO + rechazo `401` sobre HTTP real en puerto efímero (`0`).
- **`ClienteAutenticacionTest`** (3 tests): validación de campos vacíos en cliente sin llamada de red, flujo completo cliente-servidor (`login`, `verificarSesionActivaEnServidor`, `cerrarSesion`), y login SSO / manejo de credenciales inválidas.

---

## 5. Ejecución local

1. Compilar y ejecutar todas las pruebas:
   ```bash
   mvn -B verify
   ```
2. Arrancar el servidor (puerto por defecto `8080`):
   ```bash
   java -cp servidor/target/servidor-1.0.0-SNAPSHOT.jar es.uclm.esi.iso2.B101.servidor.api.AplicacionServidor
   ```
3. Arrancar el cliente de consola:
   ```bash
   java -cp cliente/target/cliente-1.0.0-SNAPSHOT.jar es.uclm.esi.iso2.B101.cliente.presentacion.AplicacionCliente
   ```
   Usuarios de prueba precargados: `paciente1`/`paciente123`, `medico1`/`medico123`, `admision1`/`admision123`, `admin1`/`admin123` (y tokens SSO `sso-sescam-<usuario>`).
