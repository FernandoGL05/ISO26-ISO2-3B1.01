package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAgendas;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class AgendasHttpHandler implements HttpHandler {
    private static final String RUTA = "/api/agendas/disponibilidad";
    private static final Set<String> PARAMETROS = Set.of("tipoPrueba", "desde", "hasta", "duracionMinutos");
    private static final System.Logger LOGGER = System.getLogger(AgendasHttpHandler.class.getName());

    private final ServicioAgendas servicio;
    private final AutorizadorPrescripcionesDiagnosticas autorizador;

    public AgendasHttpHandler(ServicioAgendas servicio, AutorizadorPrescripcionesDiagnosticas autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioAgendas servicio,
            AutorizadorPrescripcionesDiagnosticas autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new AgendasHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responder(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (IllegalArgumentException | DateTimeException error) {
            responder(intercambio, 400, "SOLICITUD_INVALIDA", "La solicitud contiene datos inválidos");
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado consultando disponibilidad de agendas", error);
            responder(intercambio, 500, "ERROR_INTERNO", "No se pudo consultar la disponibilidad");
        } finally {
            intercambio.close();
        }
    }

    private void procesar(HttpExchange intercambio) throws IOException {
        if (!RUTA.equals(intercambio.getRequestURI().getRawPath())) {
            throw new ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
        }
        if (!"GET".equals(intercambio.getRequestMethod())) {
            intercambio.getResponseHeaders().set("Allow", "GET");
            throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
        }

        String medicoId = autenticarMedico(intercambio);
        Map<String, String> parametros = leerParametros(intercambio.getRequestURI().getRawQuery());
        List<ServicioAgendas.HuecoDisponible> huecos = servicio.consultarDisponibilidad(
                medicoId,
                parametros.get("tipoPrueba"),
                LocalDateTime.parse(parametros.get("desde")),
                LocalDateTime.parse(parametros.get("hasta")),
                Integer.parseInt(parametros.get("duracionMinutos")));
        responderJson(intercambio, 200, JsonAgendas.disponibilidad(huecos));
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

    private Map<String, String> leerParametros(String consulta) {
        if (consulta == null || consulta.isBlank()) {
            throw new IllegalArgumentException("Faltan parámetros de disponibilidad");
        }
        Map<String, String> parametros = new HashMap<>();
        for (String par : consulta.split("&", -1)) {
            int separador = par.indexOf('=');
            if (separador <= 0) {
                throw new IllegalArgumentException("Parámetro de consulta no válido");
            }
            String nombre = URLDecoder.decode(par.substring(0, separador), StandardCharsets.UTF_8);
            String valor = URLDecoder.decode(par.substring(separador + 1), StandardCharsets.UTF_8);
            if (!PARAMETROS.contains(nombre) || valor.isBlank() || parametros.putIfAbsent(nombre, valor) != null) {
                throw new IllegalArgumentException("Parámetros de consulta no válidos");
            }
        }
        if (!parametros.keySet().equals(PARAMETROS)) {
            throw new IllegalArgumentException("Faltan parámetros de disponibilidad");
        }
        return parametros;
    }

    private void responder(HttpExchange intercambio, int estado, String codigo, String mensaje) throws IOException {
        responderJson(intercambio, estado, JsonAgendas.error(codigo, mensaje));
    }

    private void responderJson(HttpExchange intercambio, int estado, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
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
