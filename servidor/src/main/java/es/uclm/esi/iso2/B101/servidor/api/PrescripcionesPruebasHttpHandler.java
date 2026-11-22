package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudPruebaDiagnostica;
import es.uclm.esi.iso2.B101.servidor.servicio.PrescripcionNoPermitidaException;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesDiagnosticas;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

public final class PrescripcionesPruebasHttpHandler implements HttpHandler {
    private static final String RUTA = "/api/prescripciones-pruebas";
    private static final int MAX_CUERPO_BYTES = 16 * 1024;
    private static final System.Logger LOGGER = System.getLogger(PrescripcionesPruebasHttpHandler.class.getName());

    private final ServicioPrescripcionesDiagnosticas servicio;
    private final AutorizadorPrescripcionesDiagnosticas autorizador;

    public PrescripcionesPruebasHttpHandler(
            ServicioPrescripcionesDiagnosticas servicio,
            AutorizadorPrescripcionesDiagnosticas autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioPrescripcionesDiagnosticas servicio,
            AutorizadorPrescripcionesDiagnosticas autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new PrescripcionesPruebasHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (PrescripcionNoPermitidaException error) {
            responderError(intercambio, 403, "PROHIBIDO", "No se puede prescribir para este paciente");
        } catch (IllegalArgumentException error) {
            responderError(intercambio, 400, "SOLICITUD_INVALIDA", "La solicitud contiene datos inválidos");
        } catch (NoSuchElementException error) {
            responderError(intercambio, 404, "NO_ENCONTRADO", "Solicitud no encontrada");
        } catch (IllegalStateException error) {
            responderError(intercambio, 409, "CONFLICTO", "La solicitud entra en conflicto con el estado actual");
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado procesando prescripciones diagnósticas", error);
            responderError(intercambio, 500, "ERROR_INTERNO", "No se pudo procesar la solicitud");
        } finally {
            intercambio.close();
        }
    }

    private void procesar(HttpExchange intercambio) throws IOException {
        Ruta ruta = buscarRuta(intercambio.getRequestURI().getRawPath());
        if (ruta == null) {
            throw new ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
        }
        if (!ruta.metodoPermitido(intercambio.getRequestMethod())) {
            intercambio.getResponseHeaders().set("Allow", ruta.metodosPermitidos());
            throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
        }
        if (intercambio.getRequestURI().getRawQuery() != null) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "No se admiten parámetros de consulta");
        }

        String medicoId = autenticarMedico(intercambio);
        switch (ruta.operacion) {
            case COLECCION -> {
                if ("POST".equals(intercambio.getRequestMethod())) {
                    crear(intercambio, medicoId);
                } else {
                    List<SolicitudPruebaDiagnostica> solicitudes = servicio.consultarSolicitudes(medicoId);
                    responderJson(intercambio, 200, JsonPrescripcionesPruebas.serializarLista(solicitudes));
                }
            }
            case DETALLE -> {
                SolicitudPruebaDiagnostica solicitud = servicio.consultarDetalle(medicoId, ruta.id);
                responderJson(intercambio, 200, JsonPrescripcionesPruebas.serializar(solicitud));
            }
        }
    }

    private void crear(HttpExchange intercambio, String medicoId) throws IOException {
        JsonPrescripcionesPruebas.DatosCreacion datos = parsearCuerpo(intercambio);
        PrioridadPrueba prioridad;
        try {
            prioridad = PrioridadPrueba.valueOf(datos.prioridad());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("prioridad no válida", error);
        }
        SolicitudPruebaDiagnostica solicitud = servicio.prescribir(
                medicoId,
                datos.pacienteId(),
                datos.tipoPrueba(),
                datos.indicacionClinica(),
                datos.justificacion(),
                prioridad);
        responderJson(intercambio, 201, JsonPrescripcionesPruebas.serializar(solicitud));
    }

    private JsonPrescripcionesPruebas.DatosCreacion parsearCuerpo(HttpExchange intercambio) throws IOException {
        List<String> tiposContenido = intercambio.getRequestHeaders().get("Content-Type");
        if (tiposContenido == null
                || tiposContenido.size() != 1
                || !esTipoJson(tiposContenido.get(0))) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "El cuerpo debe utilizar application/json");
        }

        byte[] cuerpo = intercambio.getRequestBody().readNBytes(MAX_CUERPO_BYTES + 1);
        if (cuerpo.length > MAX_CUERPO_BYTES) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "El cuerpo supera el tamaño permitido");
        }
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(cuerpo))
                    .toString();
            return JsonPrescripcionesPruebas.parsearCreacion(json);
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("El cuerpo debe estar codificado en UTF-8", error);
        }
    }

    private boolean esTipoJson(String tipoContenido) {
        int inicioParametros = tipoContenido.indexOf(';');
        String tipo = inicioParametros < 0
                ? tipoContenido.trim()
                : tipoContenido.substring(0, inicioParametros).trim();
        return "application/json".equalsIgnoreCase(tipo);
    }

    private String autenticarMedico(HttpExchange intercambio) {
        List<String> cabeceras = intercambio.getRequestHeaders().get("Authorization");
        if (cabeceras == null || cabeceras.size() != 1 || !esBearerValido(cabeceras.get(0))) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }
        Optional<AutorizadorPrescripcionesDiagnosticas.IdentidadMedico> identidad =
                autorizador.autenticar(cabeceras.get(0));
        if (identidad.isEmpty()) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "La sesión no es válida");
        }
        AutorizadorPrescripcionesDiagnosticas.IdentidadMedico medico = identidad.orElseThrow();
        if (!medico.tieneRolMedico() || medico.medicoId() == null || medico.medicoId().isBlank()) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol MEDICO");
        }
        return medico.medicoId();
    }

    private boolean esBearerValido(String cabecera) {
        if (cabecera == null || cabecera.length() <= 7
                || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = cabecera.substring(7);
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
    }

    private Ruta buscarRuta(String ruta) {
        if (RUTA.equals(ruta)) {
            return new Ruta(Operacion.COLECCION, null);
        }
        String[] segmentos = ruta.split("/", -1);
        if (segmentos.length == 4 && segmentos[1].equals("api") && segmentos[2].equals("prescripciones-pruebas")) {
            String id = URLDecoder.decode(segmentos[3].replace("+", "%2B"), StandardCharsets.UTF_8);
            return new Ruta(Operacion.DETALLE, id);
        }
        return null;
    }

    private void responderJson(HttpExchange intercambio, int estado, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
    }

    private void responderError(HttpExchange intercambio, int estado, String codigo, String mensaje)
            throws IOException {
        responderJson(intercambio, estado, JsonPrescripcionesPruebas.error(codigo, mensaje));
    }

    private enum Operacion {
        COLECCION,
        DETALLE
    }

    private record Ruta(Operacion operacion, String id) {
        private boolean metodoPermitido(String metodo) {
            return switch (operacion) {
                case COLECCION -> "GET".equals(metodo) || "POST".equals(metodo);
                case DETALLE -> "GET".equals(metodo);
            };
        }

        private String metodosPermitidos() {
            return operacion == Operacion.COLECCION ? "GET, POST" : "GET";
        }
    }

    private static final class ErrorHttp extends RuntimeException {
        private final int estado;
        private final String codigo;

        private ErrorHttp(int estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }
    }
}
