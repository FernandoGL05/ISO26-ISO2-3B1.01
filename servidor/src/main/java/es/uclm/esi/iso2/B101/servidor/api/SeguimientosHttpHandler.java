package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioSeguimientos;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SeguimientosHttpHandler implements HttpHandler {
    private static final String RUTA = "/api/seguimientos";
    private static final System.Logger LOGGER = System.getLogger(SeguimientosHttpHandler.class.getName());

    private final ServicioSeguimientos servicio;
    private final AutorizadorSeguimientos autorizador;

    public SeguimientosHttpHandler(ServicioSeguimientos servicio, AutorizadorSeguimientos autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor, ServicioSeguimientos servicio, AutorizadorSeguimientos autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new SeguimientosHttpHandler(servicio, autorizador));
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
            responderError(intercambio, 404, "NO_ENCONTRADO", "Seguimiento no encontrado");
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado consultando seguimientos", error);
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
        if (!"GET".equals(intercambio.getRequestMethod())) {
            intercambio.getResponseHeaders().set("Allow", "GET");
            throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
        }
        if (intercambio.getRequestURI().getRawQuery() != null) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "No se admiten parámetros de consulta");
        }

        String pacienteId = autenticarPaciente(intercambio);
        if (ruta.seguimientoId == null) {
            List<SeguimientoPaciente> seguimientos = servicio.consultarSeguimientos(pacienteId);
            responderJson(intercambio, 200, JsonSeguimientos.serializarLista(seguimientos));
        } else {
            SeguimientoPaciente seguimiento = servicio.consultarDetalle(pacienteId, ruta.seguimientoId);
            responderJson(intercambio, 200, JsonSeguimientos.serializar(seguimiento));
        }
    }

    private String autenticarPaciente(HttpExchange intercambio) {
        List<String> cabeceras = intercambio.getRequestHeaders().get("Authorization");
        if (cabeceras == null || cabeceras.size() != 1 || !esBearerValido(cabeceras.get(0))) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }
        Optional<AutorizadorSeguimientos.IdentidadPaciente> identidad =
                autorizador.autenticar(cabeceras.get(0));
        if (identidad.isEmpty()) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "La sesión no es válida");
        }
        AutorizadorSeguimientos.IdentidadPaciente paciente = identidad.orElseThrow();
        if (!paciente.tieneRolPaciente() || paciente.pacienteId() == null || paciente.pacienteId().isBlank()) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol PACIENTE");
        }
        return paciente.pacienteId();
    }

    private static Ruta buscarRuta(String ruta) {
        if (RUTA.equals(ruta)) {
            return new Ruta(null);
        }
        String[] segmentos = ruta.split("/", -1);
        if (segmentos.length == 4
                && segmentos[1].equals("api")
                && segmentos[2].equals("seguimientos")) {
            String id = URLDecoder.decode(segmentos[3].replace("+", "%2B"), StandardCharsets.UTF_8);
            if (id.isBlank()) {
                throw new IllegalArgumentException("seguimientoId no puede estar vacío");
            }
            return new Ruta(id);
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
        responderJson(intercambio, estado, JsonSeguimientos.error(codigo, mensaje));
    }

    private record Ruta(String seguimientoId) {
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
