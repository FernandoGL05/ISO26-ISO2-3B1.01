# Documentación técnica — CU20, CU21 y CU22 (Configuración administrativa)

## 1. Alcance y trazabilidad

| Requisito | Caso de uso | Actor | Descripción |
| :--- | :--- | :--- | :--- |
| **`RF20`** | **`CU20`** | Administrador | Configurar el catálogo de tipos de prueba y sus atributos |
| **`RF21`** | **`CU21`** | Administrador | Configurar protocolos clínicos y técnicos |
| **`RF22`** | **`CU22`** | Administrador | Configurar horarios operativos de salas, equipos y médicos |

---

## 2. Versionado SemVer 2.0.0 y estrategia de ramas

- **Rama de trabajo:** `feature/CU20-CU21-CU22-configuracion-admin` creada desde `develop`, destinada a integrarse en `develop` mediante *Pull Request*.
- **Versionado SemVer 2.0.0:** al tratarse de nueva funcionalidad compatible hacia atrás (sin cambios disruptivos en las APIs ni constructores existentes), se realiza un incremento **MINOR**:
  - POM padre (`radiodiagnostico`): `0.1.0-SNAPSHOT` → `0.2.0-SNAPSHOT`.
  - Módulo `servidor`: `1.0.0-SNAPSHOT` → `1.1.0-SNAPSHOT` (actualizando `<parent>` según `D-002`).
  - Módulo `cliente`: `1.0.0-SNAPSHOT` → `1.1.0-SNAPSHOT` (actualizando `<parent>` según `D-002`).

---

## 3. Arquitectura e integración con el sistema existente

La implementación respeta estrictamente la organización por capas definida en `D-004` (`dominio`, `persistencia`, `servicio`, `api`) y la separación de clientes por caso de uso (`cu20`, `cu21`, `cu22`):

### 3.1. Módulo `servidor`

1. **Dominio (`es.uclm.esi.iso2.B101.servidor.dominio`):**
   - `TipoPrueba` (**CU20**): modalidad, equipo y sala requeridos, duración estimada (1–1440 min), preparación previa, uso de contraste y consentimiento informado (regla clínica: si requiere contraste exige consentimiento informado), requisitos de seguridad, `PrioridadPrueba` (reutilizando el enum existente del proyecto), seguimiento periódico y estado activo/inactivo.
   - `Protocolo` (**CU21**): código único, nombre, referencia `tipoPruebaId`, descripción, pasos de preparación, requisitos de seguridad, uso de contraste, número de versión (`>= 1`, autoincremental al actualizar) y estado activo/inactivo.
   - `TipoRecursoHorario` (`SALA`, `EQUIPO`, `MEDICO`) y `HorarioOperativo` (**CU22**): recurso, día de la semana (`DayOfWeek`), franja `horaInicio < horaFin`, duración de hueco, estado activo y detección de solapamientos (`seSolapaCon`).
2. **Persistencia (`es.uclm.esi.iso2.B101.servidor.persistencia`):**
   - `RepositorioTiposPrueba` / `RepositorioTiposPruebaMemoria`
   - `RepositorioProtocolos` / `RepositorioProtocolosMemoria`
   - `RepositorioHorarios` / `RepositorioHorariosMemoria`
3. **Servicios (`es.uclm.esi.iso2.B101.servidor.servicio`):**
   - `ServicioTiposPrueba`: alta (`TP-*`), edición, cambio de estado y unicidad de nombre.
   - `ServicioProtocolos`: alta (`PROT-*`), edición con incremento de versión, cambio de estado, unicidad de código y verificación referencial contra `RepositorioTiposPrueba` (CU20).
   - `ServicioHorarios`: alta (`HOR-*`), edición, cambio de estado y prevención de solapamientos en horarios activos del mismo recurso y día.
   - **Integración en `ServicioAgendas`:** se conserva el constructor original `ServicioAgendas(RepositorioCitas)` y se añade un constructor sobrecargado con `RepositorioHorarios` y `RepositorioTiposPrueba` junto al método `consultarDisponibilidadConfigurada(medicoId, tipoPruebaId, fecha)`, que calcula huecos libres a partir de los turnos configurados (CU22), la duración del tipo de prueba activo (CU20) y las citas no canceladas.
4. **API HTTP/REST (`es.uclm.esi.iso2.B101.servidor.api`):**
   - `AutorizadorAdministrador`: contrato de autorización inyectable para validar sesiones con rol `ADMINISTRADOR` (`401` si no hay sesión válida, `403` si no tiene rol administrador).
   - `TiposPruebaHttpHandler` (`/api/tipos-prueba`) + `JsonTiposPrueba`
   - `ProtocolosHttpHandler` (`/api/protocolos`) + `JsonProtocolos`
   - `HorariosHttpHandler` (`/api/horarios`) + `JsonHorarios`
   - `HttpAdminSupport`: utilidades compartidas de validación Bearer, lectura UTF-8 acotada, cabeceras seguras (`Cache-Control: no-store`, `X-Content-Type-Options: nosniff`) y serialización/parseo JSON.

### 3.2. Módulo `cliente`

- `es.uclm.esi.iso2.B101.cliente.cu20.ClienteTiposPruebaHttp`
- `es.uclm.esi.iso2.B101.cliente.cu21.ClienteProtocolosHttp`
- `es.uclm.esi.iso2.B101.cliente.cu22.ClienteHorariosHttp`

Cada cliente valida parámetros localmente antes de emitir peticiones HTTP, envía la cabecera `Authorization: Bearer <token>` y devuelve `ResultadoHttp(codigoHttp, cuerpoJson)`.

---

## 4. Verificación y pruebas automatizadas

Todas las pruebas se ejecutan con Maven y JUnit Jupiter mediante:

```bash
mvn -B clean verify
```

Cobertura de pruebas añadida:
- **Dominio:** `TipoPruebaTest`, `ProtocoloTest`, `HorarioOperativoTest`.
- **Servicios e integración:** `ServicioTiposPruebaTest`, `ServicioProtocolosTest`, `ServicioHorariosTest` y ampliación de `ServicioAgendasTest` (`integraTiposPruebaYHorariosConfiguradosAlConsultarDisponibilidad`).
- **API HTTP:** `TiposPruebaHttpHandlerTest`, `ProtocolosHttpHandlerTest`, `HorariosHttpHandlerTest` (ciclo CRUD completo, control de roles `401`/`403`, métodos `405`, validaciones `400`, no encontrado `404` y conflictos `409`).
- **Clientes HTTP:** `ClienteTiposPruebaHttpTest`, `ClienteProtocolosHttpTest`, `ClienteHorariosHttpTest`.
