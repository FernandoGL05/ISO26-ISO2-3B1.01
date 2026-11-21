package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.Notificacion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioNotificaciones;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

public final class NotificacionesHttpHandler implements HttpHandler {
    private static final System.Logger LOGGER = System.getLogger(NotificacionesHttpHandler.class.getName());

    private final ServicioNotificaciones servicio;
    private final AutorizadorNotificaciones autorizador;

    public NotificacionesHttpHandler(ServicioNotificaciones servicio, AutorizadorNotificaciones autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor, ServicioNotificaciones servicio, AutorizadorNotificaciones autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext("/api/notificaciones", new NotificacionesHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (IllegalArgumentException error) {
            responderError(intercambio, 400, "SOLICITUD_INVALIDA", "La solicitud contiene datos inválidos");
        } catch (NoSuchElementException error) {
            responderError(intercambio, 404, "NO_ENCONTRADO", "Notificación no encontrada");
        } catch (IllegalStateException error) {
            responderError(intercambio, 409, "ESTADO_INCOMPATIBLE", error.getMessage());
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado procesando notificaciones", error);
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
        if (ruta.operacion == Operacion.CONSULTAR) {
            responderJson(intercambio, 200, JsonNotificaciones.lista(servicio.consultarNotificaciones(pacienteId)));
            return;
        }

        Notificacion notificacion = servicio.marcarComoLeida(pacienteId, ruta.notificacionId);
        responderJson(intercambio, 200, JsonNotificaciones.notificacion(notificacion));
    }

    private AutorizadorNotificaciones.IdentidadPaciente autenticarPaciente(HttpExchange intercambio) {
        List<String> cabeceras = intercambio.getRequestHeaders().get("Authorization");
        if (cabeceras == null || cabeceras.size() != 1) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }
        String cabecera = cabeceras.get(0);
        if (!esCabeceraBearerValida(cabecera)) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }

        Optional<AutorizadorNotificaciones.IdentidadPaciente> identidad = autorizador.autenticar(cabecera);
        if (identidad.isEmpty()) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "La sesión no es válida");
        }
        AutorizadorNotificaciones.IdentidadPaciente paciente = identidad.orElseThrow();
        if (!paciente.tieneRolPaciente() || paciente.pacienteId() == null || paciente.pacienteId().isBlank()) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol PACIENTE");
        }
        return paciente;
    }

    private boolean esCabeceraBearerValida(String cabecera) {
        if (cabecera == null || cabecera.length() <= 7 || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = cabecera.substring(7);
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
    }

    private Ruta buscarRuta(String ruta) {
        if ("/api/notificaciones".equals(ruta)) {
            return new Ruta("GET", Operacion.CONSULTAR, null);
        }
        String[] segmentos = ruta.split("/", -1);
        if (segmentos.length == 5
                && segmentos[1].equals("api")
                && segmentos[2].equals("notificaciones")
                && segmentos[4].equals("leida")) {
            String id = URLDecoder.decode(segmentos[3].replace("+", "%2B"), StandardCharsets.UTF_8);
            return new Ruta("POST", Operacion.MARCAR_COMO_LEIDA, id);
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
        responderJson(intercambio, estado, JsonNotificaciones.error(codigo, mensaje));
    }

    private enum Operacion {
        CONSULTAR,
        MARCAR_COMO_LEIDA
    }

    private record Ruta(String metodo, Operacion operacion, String notificacionId) {
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
