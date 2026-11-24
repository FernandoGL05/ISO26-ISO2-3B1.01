# ISO26-ISO2-3B1.01

## Estado y alcance

Proyecto académico en Java 21 y Maven multimódulo, con los casos de uso CU01–CU12 integrados en esta rama. El servidor expone una API HTTP/JSON; la aplicación de consola cliente implementa únicamente CU01 (inicio, consulta y cierre de sesión). Los demás casos de uso se pueden probar mediante sus endpoints HTTP.

Esta versión usa repositorios en memoria, HTTP sin TLS, cuentas ficticias y un proveedor SSO simulado solo para demostración. No es un sistema clínico ni debe exponerse fuera de la máquina local.

## Requisitos y comandos

- JDK 21 (el proyecto configura `maven.compiler.release=21`).
- Maven instalado. El proyecto no fija una versión concreta de Maven ni incluye Maven Wrapper.

Desde la raíz del repositorio:

```powershell
mvn clean verify
```

El comando compila los módulos y ejecuta las pruebas, incluidas las comprobaciones configuradas de JaCoCo. Para ejecutar el servidor y el cliente después de compilar:

Abre dos terminales diferentes en la raíz del repositorio. Ejecuta el servidor en la primera:

```powershell
java -cp servidor/target/classes es.uclm.esi.iso2.B101.servidor.api.AplicacionServidor
```

Y el cliente en la segunda:

```powershell
java -cp cliente/target/classes es.uclm.esi.iso2.B101.cliente.presentacion.AplicacionCliente
```

El servidor usa el puerto `8080` por defecto. Se puede pasar otro puerto como argumento; el cliente admite una URL de servidor como primer argumento. Ambos usan `127.0.0.1` por defecto.

## Casos de uso integrados

Los endpoints protegidos requieren `Authorization: Bearer <token>`. Los endpoints de escritura reciben JSON y las respuestas JSON usan los códigos HTTP indicados por cada operación.

| CU | Funcionalidad implementada | Endpoints HTTP | Rol autorizado |
|---|---|---|---|
| CU01 | Login local, login SSO simulado, validar sesión y logout. | `POST /api/auth/login`, `POST /api/auth/sso`, `GET /api/auth/session`, `POST /api/auth/logout` | Todos los roles para iniciar sesión; la sesión conserva el rol autenticado. |
| CU02 | Consultar citas y solicitudes; confirmar una cita o solicitar cambios/cancelación. | `GET /api/citas`, `POST /api/citas/{id}/confirmacion`, `POST /api/citas/{id}/solicitudes/cambio`, `POST /api/citas/{id}/solicitudes/cancelacion`, `GET /api/solicitudes-cita` | Paciente propietario. |
| CU03 | Consultar notificaciones y marcar una como leída. | `GET /api/notificaciones`, `POST /api/notificaciones/{id}/leida` | Paciente propietario. |
| CU04 | Consultar el historial de pruebas realizadas. | `GET /api/historial-pruebas` | Paciente autenticado; el historial se filtra por su identidad clínica. |
| CU05 | Comunicar una incidencia, consultar la lista y el detalle. | `POST /api/incidencias`, `GET /api/incidencias`, `GET /api/incidencias/{id}` | Paciente autenticado; las incidencias se asocian a su identidad. |
| CU06 | Consultar encuestas, responder y consultar respuestas propias. | `GET /api/encuestas`, `POST /api/encuestas/{id}/respuestas`, `GET /api/encuestas/mis-respuestas` | Paciente autenticado. |
| CU07 | Consultar seguimientos y su detalle. | `GET /api/seguimientos`, `GET /api/seguimientos/{id}` | Paciente propietario. |
| CU08 | Registrar y consultar prescripciones de pruebas diagnósticas. | `POST /api/prescripciones-pruebas`, `GET /api/prescripciones-pruebas`, `GET /api/prescripciones-pruebas/{id}` | Médico autenticado y autorizado para el paciente vinculado. |
| CU09 | Registrar y consultar prescripciones de seguimiento periódico y necesidades de cita. | `POST /api/prescripciones-seguimiento`, `GET /api/prescripciones-seguimiento`, `GET /api/prescripciones-seguimiento/{id}`, `GET /api/prescripciones-seguimiento/{id}/necesidades-cita` | Médico autenticado y autorizado para el paciente vinculado. |
| CU10 | Consultar huecos disponibles para un tipo de prueba en un intervalo de un único día. | `GET /api/agendas/disponibilidad?tipoPrueba=…&desde=…&hasta=…&duracionMinutos=…` | Médico autenticado. |
| CU11 | Crear informes; el paciente consulta los suyos y el médico los que ha creado; adjuntar o descargar una imagen opcional. | `POST /api/informes-prueba`, `GET /api/informes-prueba`, `GET /api/informes-prueba/medico`, `GET /api/informes-prueba/{id}`, `POST /api/informes-prueba/{id}/imagen`, `GET /api/informes-prueba/{id}/imagen` | Médico asignado a la cita para crear o adjuntar; paciente propietario o médico autor para consultar/descargar. |
| CU12 | Registrar una prueba como realizada o registrar una incidencia de realización. | `POST /api/realizaciones-prueba` | Médico asignado a la cita. |

### Sesiones, identidades clínicas y repositorios compartidos

`ServicioAutenticacion` y `RepositorioSesionesMemoria` validan el mismo token de sesión en los handlers protegidos. Los autorizadores obtienen rol y `usuarioId` de esa sesión, no de campos de identidad enviados en la petición, y aplican el acceso por rol y propiedad.

La configuración actual relaciona `USR-001` con `paciente-1` y `USR-002` con `medico-1`. Las cuentas de admisión y administración pueden autenticarse en CU01, pero no tienen asignaciones clínicas para las operaciones restringidas a paciente o médico.

La fábrica comparte repositorios donde un flujo necesita reflejarse en otro caso de uso:

- Las citas son compartidas por CU02, CU10, CU11 y CU12.
- El historial de pruebas es compartido por CU04, CU11 y CU12.
- Las incidencias son compartidas por CU05 y CU12.
- CU12 → CU04: registrar una realización actualiza el historial consultable por el paciente.
- CU12 → CU11: el médico asignado puede crear un informe sobre la prueba resultante; el paciente propietario puede consultarlo.
- CU12 → CU05: la incidencia de realización aparece en las incidencias del paciente.

Los repositorios son independientes en memoria y se pierden al detener el proceso.

## Seguridad implementada y respuestas HTTP

- El servidor enlaza exclusivamente a `127.0.0.1`; el cliente usa esa dirección por defecto.
- Los cuerpos de login y SSO están limitados a 16 KiB antes de acumularlos en memoria. Si se supera el límite, la respuesta es `413 Payload Too Large`.
- Las contraseñas se verifican con PBKDF2-HMAC-SHA256; se permiten como máximo 5 intentos fallidos antes del bloqueo temporal de 15 minutos. Las sesiones expiran a las 8 horas y se pueden cerrar explícitamente.
- No se registran contraseñas ni tokens de sesión en los logs de autenticación.
- `401 Unauthorized`: falta la sesión/token o no es válido, expiró o fue cerrado.
- `403 Forbidden`: la sesión es válida, pero el rol no está autorizado o el usuario no tiene permisos sobre ese recurso concreto. Algunas búsquedas de recursos ajenos pueden responder `404` para no revelar su existencia.
- La fábrica normal no acepta tokens SSO. `--demo` activa un proveedor local simulado; no se conecta a un SSO corporativo real.

El enlace local no sustituye TLS ni otros controles necesarios para un despliegue real.

## Modo de demostración

El modo de demostración es optativo y carga datos ficticios con fechas calculadas respecto al día de arranque. Para compilar y arrancar en el puerto predeterminado:

```powershell
mvn -pl servidor package
java -cp servidor/target/classes es.uclm.esi.iso2.B101.servidor.api.AplicacionServidor --demo
```

Se puede indicar el puerto antes o después de `--demo`, por ejemplo `AplicacionServidor 8081 --demo`. La fábrica habitual (`crearServidor(puerto)`) no carga fixtures; las pruebas pueden seguir suministrando sus propios datos mediante la sobrecarga de dependencias.

Las cuentas locales ficticias incluidas por la fábrica son:

| Usuario | Contraseña | Rol |
|---|---|---|
| `paciente1` | `paciente123` | `PACIENTE` |
| `medico1` | `medico123` | `MEDICO` |
| `admision1` | `admision123` | `ADMISION` |
| `admin1` | `admin123` | `ADMINISTRADOR` |

El proveedor SSO simulado solo está habilitado con `--demo` y acepta estos tokens ficticios:
`sso-sescam-paciente1`, `sso-sescam-medico1`, `sso-sescam-admision1` y `sso-sescam-admin1`.
Los datos desaparecen al detener el proceso. Este modo es académico y no apto para uso clínico ni para exponer el servidor a una red.

## Ejemplos HTTP reproducibles con PowerShell

Ejecuta el servidor en una terminal diferente con `--demo`. En una segunda terminal PowerShell, ejecuta los siguientes comandos en la misma sesión para reutilizar `$base`, `$paciente` y `$medico`. Los ejemplos asumen el puerto `8080`.

Una respuesta correcta de login incluye `exito`, `mensaje`, `token`, `usuarioId`, `username`, `nombreCompleto`, `rol`, `expiraEn` y `origenAutenticacion`. `Invoke-RestMethod` convierte el JSON en un objeto PowerShell; por eso los ejemplos extraen `$loginPaciente.token` o `$loginMedico.token` y envían ese valor en `Authorization: Bearer ...`.

### CU01 y acceso del paciente a CU02–CU07

```powershell
$base = 'http://127.0.0.1:8080'
$loginPaciente = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
  -ContentType 'application/json' `
  -Body (@{ username = 'paciente1'; password = 'paciente123' } | ConvertTo-Json -Compress)
$paciente = @{ Authorization = "Bearer $($loginPaciente.token)" }

Invoke-RestMethod -Method Get -Uri "$base/api/auth/session" -Headers $paciente
Invoke-RestMethod -Method Get -Uri "$base/api/citas" -Headers $paciente
$nuevaFecha = (Get-Date).AddDays(4).ToString("yyyy-MM-dd'T'HH:mm:ss")
Invoke-RestMethod -Method Post -Uri "$base/api/citas/DEMO-CITA-CU12/solicitudes/cambio" `
  -Headers $paciente -ContentType 'application/json' `
  -Body (@{ nuevaFechaHora = $nuevaFecha } | ConvertTo-Json -Compress)
Invoke-RestMethod -Method Post -Uri "$base/api/citas/DEMO-CITA-CU12/confirmacion" -Headers $paciente
Invoke-RestMethod -Method Get -Uri "$base/api/notificaciones" -Headers $paciente
Invoke-RestMethod -Method Post -Uri "$base/api/notificaciones/DEMO-NOTIFICACION-PREPARACION/leida" -Headers $paciente
Invoke-RestMethod -Method Get -Uri "$base/api/historial-pruebas" -Headers $paciente
Invoke-RestMethod -Method Get -Uri "$base/api/incidencias" -Headers $paciente
Invoke-RestMethod -Method Get -Uri "$base/api/incidencias/DEMO-INCIDENCIA-CONSULTA" -Headers $paciente
Invoke-RestMethod -Method Get -Uri "$base/api/encuestas" -Headers $paciente
Invoke-RestMethod -Method Get -Uri "$base/api/seguimientos" -Headers $paciente
Invoke-RestMethod -Method Get -Uri "$base/api/seguimientos/DEMO-SEGUIMIENTO-TAC" -Headers $paciente

Invoke-RestMethod -Method Post -Uri "$base/api/encuestas/DEMO-ENCUESTA-SATISFACCION/respuestas" `
  -Headers $paciente -ContentType 'application/json' `
  -Body (@{ puntuacion = 5; comentario = 'Demostracion academica' } | ConvertTo-Json -Compress)
```

### CU08–CU10 para el médico

```powershell
$loginMedico = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
  -ContentType 'application/json' `
  -Body (@{ username = 'medico1'; password = 'medico123' } | ConvertTo-Json -Compress)
$medico = @{ Authorization = "Bearer $($loginMedico.token)" }

$prescripcionPrueba = Invoke-RestMethod -Method Post -Uri "$base/api/prescripciones-pruebas" `
  -Headers $medico -ContentType 'application/json' `
  -Body (@{
    pacienteId = 'paciente-1'; tipoPrueba = 'TAC'
    indicacionClinica = 'Control'; justificacion = 'Demostracion'
    prioridad = 'ORDINARIA'
  } | ConvertTo-Json -Compress)
Invoke-RestMethod -Method Get -Uri "$base/api/prescripciones-pruebas/$($prescripcionPrueba.id)" -Headers $medico

$fechaRevision = (Get-Date).Date.AddMonths(3).ToString('yyyy-MM-dd')
$prescripcionSeguimiento = Invoke-RestMethod -Method Post -Uri "$base/api/prescripciones-seguimiento" `
  -Headers $medico -ContentType 'application/json' `
  -Body (@{
    pacienteId = 'paciente-1'; tipoPrueba = 'TAC'
    motivoClinico = 'Control periodico'; periodicidadMeses = 6
    fechaPrimeraRevision = $fechaRevision; prioridad = 'ORDINARIA'
  } | ConvertTo-Json -Compress)
Invoke-RestMethod -Method Get -Uri "$base/api/prescripciones-seguimiento/$($prescripcionSeguimiento.id)/necesidades-cita" -Headers $medico

$dia = (Get-Date).Date.AddDays(2)
$desde = $dia.AddHours(8).ToString("yyyy-MM-dd'T'HH:mm")
$hasta = $dia.AddHours(14).ToString("yyyy-MM-dd'T'HH:mm")
$consultaAgenda = "$base/api/agendas/disponibilidad?tipoPrueba=TAC&desde=$([uri]::EscapeDataString($desde))&hasta=$([uri]::EscapeDataString($hasta))&duracionMinutos=30"
Invoke-RestMethod -Method Get -Uri $consultaAgenda -Headers $medico
```

### CU12 → CU04 y CU11: realizar una prueba, verla en el historial y crear el informe

```powershell
$realizacion = Invoke-RestMethod -Method Post -Uri "$base/api/realizaciones-prueba" `
  -Headers $medico -ContentType 'application/json' `
  -Body (@{ citaId = 'DEMO-CITA-CU12'; resultado = 'REALIZADA' } | ConvertTo-Json -Compress)

$historialPaciente = Invoke-RestMethod -Method Get -Uri "$base/api/historial-pruebas" -Headers $paciente
$historialPaciente

$informe = Invoke-RestMethod -Method Post -Uri "$base/api/informes-prueba" `
  -Headers $medico -ContentType 'application/json' `
  -Body (@{
    pruebaId = $realizacion.id
    hallazgos = 'Hallazgos de demostracion'
    conclusion = 'Informe academico'
  } | ConvertTo-Json -Compress)

Invoke-RestMethod -Method Get -Uri "$base/api/informes-prueba" -Headers $paciente
```

La cita `DEMO-CITA-CU12` está asignada a `medico-1` y pertenece a `paciente-1`. La respuesta de CU12 proporciona el `id` de la prueba que se usa al crear el informe con CU11.

### CU12 → CU05: registrar incidencia y consultarla como paciente

```powershell
Invoke-RestMethod -Method Post -Uri "$base/api/realizaciones-prueba" `
  -Headers $medico -ContentType 'application/json' `
  -Body (@{
    citaId = 'DEMO-CITA-INCIDENCIA'
    resultado = 'INCIDENCIA'
    descripcion = 'Incidencia ficticia de demostracion'
  } | ConvertTo-Json -Compress)

Invoke-RestMethod -Method Get -Uri "$base/api/incidencias" -Headers $paciente
```

Para comprobar el SSO simulado, sustituye el login local por:

```powershell
$loginSso = Invoke-RestMethod -Method Post -Uri "$base/api/auth/sso" `
  -ContentType 'application/json' `
  -Body (@{ ssoToken = 'sso-sescam-medico1' } | ConvertTo-Json -Compress)
$medicoSso = @{ Authorization = "Bearer $($loginSso.token)" }
Invoke-RestMethod -Method Get -Uri "$base/api/auth/session" -Headers $medicoSso
```

Para comprobar que el logout invalida la sesión, ejecuta al final:

```powershell
Invoke-RestMethod -Method Post -Uri "$base/api/auth/logout" -Headers $paciente
Invoke-RestMethod -Method Get -Uri "$base/api/citas" -Headers $paciente
```

La primera petición cierra la sesión; la segunda debe responder `401 Unauthorized`.

## Datos de las fixtures de demostración

Además de las instrucciones de arranque y las credenciales anteriores, `--demo` carga en los mismos repositorios compartidos:

- Una cita realizada y su entrada de historial, con fechas relativas al arranque.
- `DEMO-CITA-CU12`, asignada a `medico-1` y perteneciente a `paciente-1`, para el flujo CU12 → CU04 → CU11.
- `DEMO-CITA-INCIDENCIA`, asociada al mismo paciente y médico, para el flujo CU12 → CU05.
- Una notificación de preparación vinculada a la cita de CU12, una incidencia ficticia, una encuesta y un seguimiento.

La fecha de `DEMO-CITA-CU12` está fijada dos días después del arranque; el servicio CU12 no comprueba que la hora de realización sea posterior o igual a la cita. Por tanto, este dato sirve para la demostración del flujo, pero no acredita una validación temporal clínica.

## Consulta de disponibilidad de agendas

El personal médico puede consultar los huecos libres de su agenda diaria mediante:

```text
GET /api/agendas/disponibilidad?tipoPrueba=RM&desde=<AAAA-MM-DD>T09:00&hasta=<AAAA-MM-DD>T13:00&duracionMinutos=30
Authorization: Bearer <token>
```

Sustituye `AAAA-MM-DD` por la fecha de un mismo día. La identidad del médico se obtiene del token, no de parámetros enviados por el cliente. `desde` y `hasta` delimitan el intervalo consultado y deben corresponder al mismo día. La respuesta es una lista JSON de intervalos disponibles, con el tipo de prueba solicitado.

Las citas asociadas al médico bloquean intervalos de la duración indicada; las citas canceladas no bloquean huecos. Para asociar una cita a un médico se utiliza el constructor de `Cita` que recibe `medicoId`. Las citas antiguas creadas sin esa asociación no se atribuyen a ninguna agenda. No hay horarios laborales configurados: el servicio calcula huecos dentro del intervalo solicitado, sin modelar turnos reales.

## CDU11: informes de pruebas

- `POST /api/informes-prueba`: solo el médico autenticado asignado a la cita vinculada a la prueba realizada puede crear el informe, con `pruebaId`, `hallazgos` y `conclusion` en JSON. La cita debe constar como realizada y coincidir con el paciente y el tipo de prueba del historial. Si no puede acreditarse esa relación, se rechaza la operación. La fecha de creación la establece el servidor.
- `GET /api/informes-prueba`: el paciente autenticado consulta sus informes.
- `GET /api/informes-prueba/medico`: el médico autenticado consulta los informes que ha creado.
- `GET /api/informes-prueba/{id}`: consulta el detalle si la identidad autenticada es el paciente propietario o el médico autor.
- `POST /api/informes-prueba/{id}/imagen`: el médico autor adjunta una imagen opcional mediante los campos JSON `tipoImagen` (`image/png` o `image/jpeg`) e `imagenBase64`.
- `GET /api/informes-prueba/{id}/imagen`: el paciente propietario o el médico autor descarga la imagen.

Las imágenes se almacenan en memoria, tienen un tamaño máximo de 5 MiB y se comprueba que la firma binaria coincida con PNG o JPEG. El repositorio en memoria no conserva los informes ni las imágenes al reiniciar el proceso.

## CDU12: registrar realización de prueba o incidencia

El médico asignado a una cita puede registrar el resultado de su realización:

```text
POST /api/realizaciones-prueba
Authorization: Bearer <token>
Content-Type: application/json

{"citaId":"cita-1","resultado":"REALIZADA"}
```

La respuesta registra la prueba en el historial del paciente y cambia el estado de la cita a `REALIZADA`. No se permite registrar por segunda vez una prueba ya realizada ni registrar resultados de citas canceladas o asignadas a otro médico.
El servicio no valida la fecha/hora de la cita frente al momento actual; la validación temporal sigue pendiente de contrastarse con los requisitos de CU12.

Si la prueba no pudo completarse, se puede registrar una incidencia asociada a la cita:

```json
{"citaId":"cita-1","resultado":"INCIDENCIA","descripcion":"El equipo falló durante la adquisición"}
```

La incidencia queda en el CU05 del paciente. En esta versión el registro se almacena en los repositorios en memoria existentes.

## Pruebas y estado verificado

El estado documentado de la rama es de **298 pruebas superadas**: 249 del servidor y 49 del cliente. `mvn clean verify` y las comprobaciones configuradas de JaCoCo finalizaron correctamente. No se publica aquí un porcentaje de cobertura. Existen pruebas de integración HTTP para CU01, las autorizaciones de los casos de uso, la carga de fixtures de demostración y los flujos CU12 → CU04/CU05/CU11. El cliente de consola y la API se han arrancado manualmente; también se comprobaron login, consulta de sesión, logout y SSO simulado.

## Limitaciones actuales

- Los repositorios son volátiles y en memoria; no hay persistencia duradera ni base de datos.
- El SSO es un proveedor simulado disponible exclusivamente con `--demo`; no hay SSO externo.
- Las cuentas, asociaciones y datos de demostración son ficticios.
- El servidor es HTTP, está limitado a la interfaz local y tiene finalidad académica; no ofrece TLS ni está preparado para uso clínico o producción.
- La interfaz de consola cliente cubre CU01; CU02–CU12 se acceden mediante HTTP.
- CU09 genera necesidades de citas, pero no crea automáticamente citas ni registros de CU07.
- No se generan automáticamente todas las notificaciones que podrían derivarse de las operaciones.
- Las agendas no tienen horarios laborales configurados.
- CU12 no contrasta temporalmente la fecha de la cita con el momento del registro; validar este comportamiento frente a los requisitos sigue pendiente.
- Las pruebas existentes cubren integraciones y escenarios principales, no todas las operaciones, combinaciones de permisos ni casos límite posibles.
