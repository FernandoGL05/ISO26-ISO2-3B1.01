# Documentación de implementación — CU01: Iniciar sesión (`RF01`)

## 1. Contexto y requisitos cubiertos

- **Requisito funcional:** `RF01` — Iniciar sesión en el sistema.
- **Caso de uso:** `CU01` — Iniciar sesión.
- **Actores:** Todos los actores del sistema (`PACIENTE`, `MEDICO`, `ADMISION`, `ADMINISTRADOR`).
- **Acuerdos del proyecto aplicados:**
  - **Estructura Maven multimódulo (`D-002`):** implementación dividida en `servidor` y `cliente`.
  - **División cliente-servidor (Acta 3):** el flujo de `CU01` cubre tanto la lógica y API del servidor como la validación, comunicación y presentación del cliente.
  - **Requisitos técnicos (`RT01`–`RT05`):** arquitectura cliente-servidor sobre HTTP/REST + JSON (compatible con futuros clientes de escritorio, web y móvil) y puerto desacoplado para una futura integración SSO. El proveedor disponible actualmente es simulado y solo se habilita en modo de demostración; no existe conexión al SSO corporativo del SESCAM.

---

## 2. Arquitectura y estructura de código

Se ha implementado `CU01` en **Java 21** sin dependencias externas de ejecución (únicamente `junit-jupiter` en ámbito `test`), siguiendo una arquitectura por capas:

### 2.1. Módulo `servidor` (`es.uclm.esi.iso2.B101.servidor`)

| Capa | Clases | Responsabilidad |
| :--- | :--- | :--- |
| **Dominio** (`dominio`) | `Rol`, `Usuario`, `Sesion`, `ResultadoAutenticacion`, `RegistroAuditoriaAcceso` | Modelos del negocio: roles de los 4 actores, estado de cuenta (activa/bloqueada, contador de intentos fallidos), sesiones con expiración y registros de auditoría de acceso (base para `RF25`). |
| **Seguridad** (`seguridad`) | `GestorContrasenas`, `GeneradorTokens` | Derivación de contraseñas con **PBKDF2WithHmacSHA256** (65.536 iteraciones, salt aleatorio de 16 bytes y comparación en tiempo constante) y generación de tokens de sesión criptográficamente seguros (`SecureRandom` + Base64 URL-safe). |
| **Persistencia** (`persistencia`) | `RepositorioUsuarios`, `RepositorioUsuariosMemoria`, `RepositorioSesiones`, `RepositorioSesionesMemoria`, `RepositorioAuditoriaAcceso`, `RepositorioAuditoriaAccesoMemoria` | Contratos de repositorio e implementaciones en memoria *thread-safe* (`ConcurrentHashMap` / `CopyOnWriteArrayList`). La fábrica incluye cuatro cuentas locales ficticias (`paciente1`, `medico1`, `admision1`, `admin1`); las fixtures clínicas adicionales solo se cargan en modo `--demo`. |
| **Integración** (`integracion`) | `ProveedorSSO`, `ProveedorSSOSimulado` | Puerto desacoplado para una posible integración futura e implementación simulada habilitada exclusivamente por el modo de demostración. |
| **Servicio** (`servicio`) | `ServicioAutenticacion` | Lógica central de `CU01`: validación de credenciales, verificación de cuenta activa, bloqueo temporal tras superar el máximo de intentos fallidos, autenticación delegada vía SSO, validación de vigencia de sesión, cierre de sesión y registro de auditoría. Recibe un `Clock` inyectable para pruebas deterministas. |
| **API HTTP** (`api`) | `ServidorHTTP`, `JsonUtil`, `AplicacionServidor` | Servidor HTTP/REST basado en `com.sun.net.httpserver.HttpServer` con hilos virtuales de Java 21 y serialización JSON propia sin dependencias externas. |

#### Endpoints REST expuestos por el servidor

| Método | Ruta | Entrada | Respuestas |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | `{"username":"...","password":"..."}` | `200 OK` (token y datos de sesión), `400` (vacías), `401` (inválidas), `403` (inactiva), `413` (cuerpo superior a 16 KiB), `423` (bloqueada) |
| `POST` | `/api/auth/sso` | `{"ssoToken":"..."}` | `200 OK`, `401` (token SSO inválido), `403` (cuenta inactiva), `413` (cuerpo superior a 16 KiB) |
| `GET` | `/api/auth/session` | Cabecera `Authorization: Bearer <token>` | `200 OK` (sesión vigente), `401` (ausente/expirada/cerrada) |
| `POST` | `/api/auth/logout` | Cabecera `Authorization: Bearer <token>` | `200 OK`, `401` (token inválido) |

### Integración de CU01 con CU02–CU12

El servidor HTTP comparte `ServicioAutenticacion` y el repositorio de sesiones con todos los handlers protegidos de CU02–CU12. Cada petición protegida presenta el token Bearer de CU01; el autorizador obtiene de la sesión el `usuarioId` y el rol y no confía en identidades clínicas enviadas por el cliente.

La asociación de identidades está separada porque los identificadores de autenticación y clínicos son distintos: `USR-001` → `paciente-1` y `USR-002` → `medico-1`. Los autorizadores comprueban rol y, según la operación, la relación con el paciente, cita o informe. Las cuentas `ADMISION` y `ADMINISTRADOR` pueden iniciar sesión, pero no se convierten por ello en usuarios clínicos autorizados para los endpoints exclusivos de paciente o médico.

Los repositorios compartidos hacen visibles los flujos entre casos de uso:

- Citas: CU02, CU10, CU11 y CU12.
- Historial de pruebas: CU04, CU11 y CU12.
- Incidencias: CU05 y CU12.
- CU12 → CU04: una realización registrada aparece en el historial consultado por el paciente.
- CU12 → CU11: el médico asignado crea el informe de la prueba; el paciente propietario lo consulta.
- CU12 → CU05: una incidencia de realización se consulta desde las incidencias del paciente.

La tabla de endpoints y roles para los doce casos está en [README.md](../README.md#casos-de-uso-integrados). La autenticación es real dentro del proceso local; el SSO externo no está implementado. Todo el estado persiste únicamente mientras vive el servidor.

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

El estado documentado de la rama es de **298 pruebas superadas**: 249 en el módulo servidor y 49 en el módulo cliente. `mvn clean verify` y las comprobaciones configuradas de JaCoCo finalizaron correctamente; no se indica un porcentaje de cobertura.

Además de las pruebas unitarias de CU01, existen pruebas de integración HTTP para autenticación, permisos de casos de uso, sesiones, fixtures y flujos compartidos. Entre ellas están `AplicacionServidorE2EIntegracionTest`, `SeguridadBasicaServidorIntegracionTest`, `DatosDemostracionIntegracionTest`, `AutenticacionCasosUsoIntegracionTest`, `AutenticacionCasosUsoPacienteIntegracionTest`, `AutenticacionNotificacionesHistorialIncidenciasIntegracionTest`, `AutenticacionPrescripcionesAgendasIntegracionTest` y `AutenticacionInformesRealizacionIntegracionTest`.

El estado del proyecto también recoge un arranque manual del servidor y el cliente y comprobaciones manuales de login local, consulta de sesión, logout y SSO simulado. No implica que se hayan ejercitado manualmente todas las rutas o combinaciones posibles.

---

## 5. Ejecución local

Se requiere JDK 21 y Maven instalado; el proyecto fija `maven.compiler.release=21`, pero no fija una versión de Maven ni incluye Maven Wrapper. Desde la raíz:

```powershell
mvn clean verify
java -cp servidor/target/classes es.uclm.esi.iso2.B101.servidor.api.AplicacionServidor
java -cp cliente/target/classes es.uclm.esi.iso2.B101.cliente.presentacion.AplicacionCliente
```

El servidor usa el puerto `8080` y escucha en `127.0.0.1`; acepta un puerto alternativo como argumento. Para cargar datos de demostración, activa `--demo`, por ejemplo `AplicacionServidor 8081 --demo`. La fábrica `crearServidor(puerto)` no carga las fixtures clínicas y mantiene su comportamiento habitual.

Las cuentas locales ficticias son `paciente1`/`paciente123`, `medico1`/`medico123`, `admision1`/`admision123` y `admin1`/`admin123`. El SSO simulado solo funciona con `--demo` y los tokens `sso-sescam-paciente1`, `sso-sescam-medico1`, `sso-sescam-admision1` y `sso-sescam-admin1`.

### Seguridad y límites

- Login y SSO limitan el cuerpo a 16 KiB antes de leerlo completamente; un exceso responde `413`.
- Las contraseñas se derivan con PBKDF2-HMAC-SHA256 (65.536 iteraciones y salt aleatorio de 16 bytes). Se permiten como máximo 5 intentos fallidos antes del bloqueo temporal de 15 minutos; las sesiones expiran a las 8 horas y se pueden cerrar mediante logout.
- Los logs no incluyen contraseñas ni tokens de sesión.
- `401` indica sesión ausente, no válida, expirada o cerrada; `403` indica que el rol no está autorizado o que el usuario carece de permisos sobre un recurso concreto. El acceso a ciertos recursos ajenos puede responder `404`.
- El servidor local no ofrece TLS. No es apto para uso clínico ni producción.

El cliente de consola solo implementa CU01. La demostración no dispone de persistencia duradera, SSO externo ni validación temporal completa de CU12. Véase [README.md](../README.md#limitaciones-actuales) para las limitaciones funcionales de CU09, notificaciones y agendas.
