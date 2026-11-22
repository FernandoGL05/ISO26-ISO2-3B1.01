package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.RespuestaEncuestaSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioEncuestasSatisfaccion;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EncuestasSatisfaccionHttpHandler implements HttpHandler {
    private static final String RUTA = "/api/encuestas";
    private static final int MAX_CUERPO_BYTES = 16 * 1024;
    private static final System.Logger LOGGER =
            System.getLogger(EncuestasSatisfaccionHttpHandler.class.getName());

    private final ServicioEncuestasSatisfaccion servicio;
    private final AutorizadorEncuestas autorizador;

    public EncuestasSatisfaccionHttpHandler(
            ServicioEncuestasSatisfaccion servicio, AutorizadorEncuestas autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor, ServicioEncuestasSatisfaccion servicio, AutorizadorEncuestas autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new EncuestasSatisfaccionHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (IllegalArgumentException error) {
            responderError(intercambio, 400, "SOLICITUD_INVALIDA", "La solicitud contiene datos inválidos");
        } catch (java.util.NoSuchElementException error) {
            responderError(intercambio, 404, "NO_ENCONTRADO", "Encuesta no encontrada");
        } catch (IllegalStateException error) {
            responderError(intercambio, 409, "CONFLICTO", "La encuesta no admite esta respuesta");
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado procesando encuestas", error);
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

        String pacienteId = autenticarPaciente(intercambio);
        switch (ruta.operacion) {
            case CONSULTAR_DISPONIBLES -> responderJson(
                    intercambio, 200,
                    JsonEncuestasSatisfaccion.serializarEncuestas(servicio.consultarEncuestasDisponibles()));
            case RESPONDER -> responderEncuesta(intercambio, pacienteId, ruta.encuestaId);
            case MIS_RESPUESTAS -> responderJson(
                    intercambio, 200,
                    JsonEncuestasSatisfaccion.serializarRespuestas(servicio.consultarMisRespuestas(pacienteId)));
        }
    }

    private void responderEncuesta(HttpExchange intercambio, String pacienteId, String encuestaId)
            throws IOException {
        JsonEncuestasSatisfaccion.DatosRespuesta datos = parsearCuerpo(intercambio);
        RespuestaEncuestaSatisfaccion respuesta = servicio.responderEncuesta(
                pacienteId, encuestaId, datos.puntuacion(), datos.comentario());
        responderJson(intercambio, 201, JsonEncuestasSatisfaccion.serializarRespuesta(respuesta));
    }

    private String autenticarPaciente(HttpExchange intercambio) {
        List<String> cabeceras = intercambio.getRequestHeaders().get("Authorization");
        if (cabeceras == null || cabeceras.size() != 1 || !esBearerValido(cabeceras.get(0))) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }
        Optional<AutorizadorEncuestas.IdentidadPaciente> identidad = autorizador.autenticar(cabeceras.get(0));
        if (identidad.isEmpty()) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "La sesión no es válida");
        }
        AutorizadorEncuestas.IdentidadPaciente paciente = identidad.orElseThrow();
        if (!paciente.tieneRolPaciente() || paciente.pacienteId() == null || paciente.pacienteId().isBlank()) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol PACIENTE");
        }
        return paciente.pacienteId();
    }

    private JsonEncuestasSatisfaccion.DatosRespuesta parsearCuerpo(HttpExchange intercambio) throws IOException {
        List<String> tiposContenido = intercambio.getRequestHeaders().get("Content-Type");
        if (tiposContenido == null || tiposContenido.size() != 1 || !esTipoJson(tiposContenido.get(0))) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "El cuerpo debe utilizar application/json");
        }
        byte[] cuerpo = intercambio.getRequestBody().readNBytes(MAX_CUERPO_BYTES + 1);
        if (cuerpo.length > MAX_CUERPO_BYTES) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "El cuerpo de la solicitud supera el tamaño permitido");
        }
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(cuerpo))
                    .toString();
            return JsonEncuestasSatisfaccion.parsearRespuesta(json);
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("El cuerpo debe estar codificado en UTF-8");
        }
    }

    private static boolean esTipoJson(String tipoContenido) {
        int inicioParametros = tipoContenido.indexOf(';');
        String tipo = inicioParametros < 0
                ? tipoContenido.trim()
                : tipoContenido.substring(0, inicioParametros).trim();
        return "application/json".equalsIgnoreCase(tipo);
    }

    private static Ruta buscarRuta(String ruta) {
        if (RUTA.equals(ruta)) {
            return new Ruta(Operacion.CONSULTAR_DISPONIBLES, null);
        }
        if ((RUTA + "/mis-respuestas").equals(ruta)) {
            return new Ruta(Operacion.MIS_RESPUESTAS, null);
        }
        String[] segmentos = ruta.split("/", -1);
        if (segmentos.length == 5
                && segmentos[1].equals("api")
                && segmentos[2].equals("encuestas")
                && segmentos[4].equals("respuestas")) {
            String id = URLDecoder.decode(segmentos[3].replace("+", "%2B"), StandardCharsets.UTF_8);
            if (id.isBlank()) {
                throw new IllegalArgumentException("encuestaId no puede estar vacío");
            }
            return new Ruta(Operacion.RESPONDER, id);
        }
        return null;
    }

    private static boolean esBearerValido(String cabecera) {
        if (cabecera == null || cabecera.length() <= 7
                || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = cabecera.substring(7);
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
    }

    private static void responderJson(HttpExchange intercambio, int estado, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
    }

    private static void responderError(HttpExchange intercambio, int estado, String codigo, String mensaje)
            throws IOException {
        responderJson(intercambio, estado, JsonEncuestasSatisfaccion.error(codigo, mensaje));
    }

    private enum Operacion {
        CONSULTAR_DISPONIBLES,
        RESPONDER,
        MIS_RESPUESTAS
    }

    private record Ruta(Operacion operacion, String encuestaId) {
        private boolean metodoPermitido(String metodo) {
            return switch (operacion) {
                case CONSULTAR_DISPONIBLES, MIS_RESPUESTAS -> "GET".equals(metodo);
                case RESPONDER -> "POST".equals(metodo);
            };
        }

        private String metodosPermitidos() {
            return operacion == Operacion.RESPONDER ? "POST" : "GET";
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
