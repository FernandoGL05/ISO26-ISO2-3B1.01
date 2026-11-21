package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudCita;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioCitas;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Objects;
import java.util.NoSuchElementException;
import java.util.List;

public final class Cu02HttpHandler implements HttpHandler {
    private static final int MAX_CUERPO_BYTES = 16 * 1024;
    private static final System.Logger LOGGER = System.getLogger(Cu02HttpHandler.class.getName());

    private final ServicioCitas servicioCitas;
    private final AutorizadorCitas autorizador;

    public Cu02HttpHandler(ServicioCitas servicioCitas, AutorizadorCitas autorizador) {
        this.servicioCitas = Objects.requireNonNull(servicioCitas, "servicioCitas no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(HttpServer servidor, ServicioCitas servicioCitas, AutorizadorCitas autorizador) {
        HttpServer servidorValidado = Objects.requireNonNull(servidor, "servidor no puede ser nulo");
        Cu02HttpHandler handler = new Cu02HttpHandler(servicioCitas, autorizador);
        servidorValidado.createContext("/api/citas", handler);
        servidorValidado.createContext("/api/solicitudes-cita", handler);
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (IllegalArgumentException | DateTimeException error) {
            responderError(intercambio, 400, "SOLICITUD_INVALIDA", "La solicitud contiene datos inválidos");
        } catch (NoSuchElementException error) {
            responderError(intercambio, 404, "NO_ENCONTRADO", "Cita no encontrada");
        } catch (IllegalStateException error) {
            responderError(intercambio, 409, "ESTADO_INCOMPATIBLE", error.getMessage());
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado procesando una solicitud CU02", error);
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
        if (!ruta.metodo.equals(intercambio.getRequestMethod())) {
            intercambio.getResponseHeaders().set("Allow", ruta.metodo);
            throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
        }

        String pacienteId = autenticarPaciente(intercambio).pacienteId();
        switch (ruta.operacion) {
            case CONSULTAR_CITAS ->
                    responderJson(intercambio, 200, JsonApiCitas.citas(servicioCitas.consultarCitas(pacienteId)));
            case CONFIRMAR_CITA -> {
                servicioCitas.confirmarCita(pacienteId, ruta.citaId);
                responderJson(intercambio, 200, JsonApiCitas.exito("Cita confirmada"));
            }
            case SOLICITAR_CAMBIO -> {
                LocalDateTime nuevaFechaHora = JsonApiCitas.parsearNuevaFechaHora(leerCuerpo(intercambio));
                SolicitudCita solicitud = servicioCitas.solicitarCambioFecha(
                        pacienteId, ruta.citaId, nuevaFechaHora);
                responderJson(intercambio, 201, JsonApiCitas.solicitud(solicitud));
            }
            case SOLICITAR_CANCELACION -> {
                SolicitudCita solicitud = servicioCitas.solicitarCancelacion(pacienteId, ruta.citaId);
                responderJson(intercambio, 201, JsonApiCitas.solicitud(solicitud));
            }
            case CONSULTAR_SOLICITUDES -> responderJson(
                    intercambio, 200, JsonApiCitas.solicitudes(servicioCitas.consultarSolicitudes(pacienteId)));
        }
    }

    private AutorizadorCitas.IdentidadAutorizada autenticarPaciente(HttpExchange intercambio) {
        List<String> cabeceras = intercambio.getRequestHeaders().get("Authorization");
        if (cabeceras == null || cabeceras.size() != 1) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }
        String cabecera = cabeceras.get(0);
        if (!esCabeceraBearerValida(cabecera)) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }

        Optional<AutorizadorCitas.IdentidadAutorizada> identidad = autorizador.autenticar(cabecera);
        if (identidad.isEmpty()) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "La sesión no es válida");
        }

        AutorizadorCitas.IdentidadAutorizada usuario = identidad.orElseThrow();
        if (!usuario.tieneRolPaciente() || usuario.pacienteId() == null || usuario.pacienteId().isBlank()) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol PACIENTE");
        }
        return usuario;
    }

    private boolean esCabeceraBearerValida(String cabecera) {
        if (cabecera == null || cabecera.length() <= 7 || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = cabecera.substring(7);
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
    }

    private Ruta buscarRuta(String ruta) {
        if ("/api/citas".equals(ruta)) {
            return new Ruta("GET", Operacion.CONSULTAR_CITAS, null);
        }
        if ("/api/solicitudes-cita".equals(ruta)) {
            return new Ruta("GET", Operacion.CONSULTAR_SOLICITUDES, null);
        }

        String[] segmentos = ruta.split("/", -1);
        if (segmentos.length == 5
                && segmentos[1].equals("api")
                && segmentos[2].equals("citas")
                && segmentos[4].equals("confirmacion")) {
            return new Ruta("POST", Operacion.CONFIRMAR_CITA, decodificarSegmento(segmentos[3]));
        }
        if (segmentos.length == 6
                && segmentos[1].equals("api")
                && segmentos[2].equals("citas")
                && segmentos[4].equals("solicitudes")
                && segmentos[5].equals("cambio")) {
            return new Ruta("POST", Operacion.SOLICITAR_CAMBIO, decodificarSegmento(segmentos[3]));
        }
        if (segmentos.length == 6
                && segmentos[1].equals("api")
                && segmentos[2].equals("citas")
                && segmentos[4].equals("solicitudes")
                && segmentos[5].equals("cancelacion")) {
            return new Ruta("POST", Operacion.SOLICITAR_CANCELACION, decodificarSegmento(segmentos[3]));
        }
        return null;
    }

    private String decodificarSegmento(String segmento) {
        return URLDecoder.decode(segmento.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private String leerCuerpo(HttpExchange intercambio) throws IOException {
        InputStream cuerpo = intercambio.getRequestBody();
        byte[] bytes = cuerpo.readNBytes(MAX_CUERPO_BYTES + 1);
        if (bytes.length > MAX_CUERPO_BYTES) {
            throw new IllegalArgumentException("El cuerpo de la solicitud es demasiado grande");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("El cuerpo debe estar codificado en UTF-8", error);
        }
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
        responderJson(intercambio, estado, JsonApiCitas.error(codigo, mensaje));
    }

    private enum Operacion {
        CONSULTAR_CITAS,
        CONFIRMAR_CITA,
        SOLICITAR_CAMBIO,
        SOLICITAR_CANCELACION,
        CONSULTAR_SOLICITUDES
    }

    private record Ruta(String metodo, Operacion operacion, String citaId) {
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
