package es.uclm.esi.iso2.B101.servidor.api;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAgendas;

public final class AgendasHttpHandler implements HttpHandler {
    private static final int MAX_CUERPO_BYTES = 16 * 1024;
    private static final System.Logger LOGGER =
            System.getLogger(AgendasHttpHandler.class.getName());

    private final ServicioAgendas servicio;
    private final AutorizadorAgendas autorizador;

    public AgendasHttpHandler(
            ServicioAgendas servicio,
            AutorizadorAgendas autorizador) {

        this.servicio = Objects.requireNonNull(servicio);
        this.autorizador = Objects.requireNonNull(autorizador);
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioAgendas servicio,
            AutorizadorAgendas autorizador) {

        Objects.requireNonNull(servidor).createContext(
                "/api/agendas",
                new AgendasHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responder(intercambio, error.estado,
                    JsonAgendas.error(error.codigo, error.getMessage()));
        } catch (IllegalArgumentException | DateTimeException error) {
            responder(intercambio, 400,
                    JsonAgendas.error(
                            "SOLICITUD_INVALIDA",
                            "La solicitud contiene datos inválidos"));
        } catch (NoSuchElementException error) {
            responder(intercambio, 404,
                    JsonAgendas.error(
                            "NO_ENCONTRADO",
                            "Agenda o hueco no encontrado"));
        } catch (IllegalStateException error) {
            responder(intercambio, 409,
                    JsonAgendas.error(
                            "CONFLICTO", error.getMessage()));
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Error inesperado procesando CU17", error);

            responder(intercambio, 500,
                    JsonAgendas.error(
                            "ERROR_INTERNO",
                            "No se pudo procesar la solicitud"));
        } finally {
            intercambio.close();
        }
    }

    private void procesar(HttpExchange intercambio) throws IOException {
        String[] ruta = intercambio.getRequestURI()
                .getRawPath().split("/", -1);

        if (ruta.length < 3
                || !"api".equals(ruta[1])
                || !"agendas".equals(ruta[2])) {
            throw noEncontrado();
        }

        String permitidos;
        if (ruta.length == 3) {
            permitidos = "GET, POST";
        } else if (ruta.length == 4 && !ruta[3].isBlank()) {
            permitidos = "GET";
        } else if (ruta.length == 5
                && !ruta[3].isBlank()
                && "huecos".equals(ruta[4])) {
            permitidos = "GET, POST";
        } else if (ruta.length == 6
                && !ruta[3].isBlank()
                && "huecos".equals(ruta[4])
                && !ruta[5].isBlank()) {
            permitidos = "PUT, DELETE";
        } else {
            throw noEncontrado();
        }

        exigirAdmision(intercambio);

        String metodo = intercambio.getRequestMethod();
        if (!List.of(permitidos.split(", ")).contains(metodo)) {
            intercambio.getResponseHeaders().set("Allow", permitidos);
            throw new ErrorHttp(
                    405, "METODO_NO_PERMITIDO",
                    "Método HTTP no permitido");
        }

        if (ruta.length == 3) {
            procesarColeccion(intercambio, metodo);
            return;
        }

        if (intercambio.getRequestURI().getRawQuery() != null) {
            throw new IllegalArgumentException(
                    "Esta ruta no admite parámetros de consulta");
        }

        String agendaId = segmento(ruta[3]);

        if (ruta.length == 4) {
            responder(intercambio, 200,
                    JsonAgendas.agenda(
                            servicio.consultarAgenda(agendaId)));
        } else if (ruta.length == 5) {
            procesarHuecos(intercambio, metodo, agendaId);
        } else {
            String huecoId = segmento(ruta[5]);

            if ("DELETE".equals(metodo)) {
                servicio.eliminarHueco(agendaId, huecoId);
                responder(intercambio, 200,
                        JsonAgendas.exito("Hueco eliminado"));
            } else {
                Map<String, String> datos = ParserJsonAgendas.leer(
                        leerCuerpo(intercambio),
                        "horaInicio", "horaFin");

                responder(intercambio, 200,
                        JsonAgendas.hueco(servicio.modificarHueco(
                                agendaId, huecoId,
                                LocalTime.parse(datos.get("horaInicio")),
                                LocalTime.parse(datos.get("horaFin")))));
            }
        }
    }

    private void procesarColeccion(
            HttpExchange intercambio, String metodo) throws IOException {

        Map<String, String> consulta = leerConsulta(intercambio);

        if ("POST".equals(metodo)) {
            if (!consulta.isEmpty()) {
                throw new IllegalArgumentException(
                        "La creación no admite parámetros de consulta");
            }

            Map<String, String> datos = ParserJsonAgendas.leer(
                    leerCuerpo(intercambio), "medicoId", "fecha");

            responder(intercambio, 201,
                    JsonAgendas.agenda(servicio.crearAgenda(
                            datos.get("medicoId"),
                            LocalDate.parse(datos.get("fecha")))));
            return;
        }

        if (!consulta.keySet().equals(Set.of("fecha"))
                && !consulta.keySet().equals(
                        Set.of("medicoId", "fecha"))) {
            throw new IllegalArgumentException(
                    "Se requiere fecha y, opcionalmente, medicoId");
        }

        LocalDate fecha = LocalDate.parse(consulta.get("fecha"));

        if (consulta.containsKey("medicoId")) {
            responder(intercambio, 200,
                    JsonAgendas.agenda(
                            servicio.consultarAgendaPorMedicoYFecha(
                                    consulta.get("medicoId"), fecha)));
        } else {
            responder(intercambio, 200,
                    JsonAgendas.agendas(
                            servicio.consultarAgendasPorFecha(fecha)));
        }
    }

    private void procesarHuecos(
            HttpExchange intercambio,
            String metodo,
            String agendaId) throws IOException {

        if ("GET".equals(metodo)) {
            responder(intercambio, 200,
                    JsonAgendas.huecos(
                            servicio.consultarHuecos(agendaId)));
            return;
        }

        Map<String, String> datos = ParserJsonAgendas.leer(
                leerCuerpo(intercambio), "horaInicio", "horaFin");

        responder(intercambio, 201,
                JsonAgendas.hueco(servicio.agregarHueco(
                        agendaId,
                        LocalTime.parse(datos.get("horaInicio")),
                        LocalTime.parse(datos.get("horaFin")))));
    }

    private void exigirAdmision(HttpExchange intercambio) {
        List<String> cabeceras =
                intercambio.getRequestHeaders().get("Authorization");

        if (cabeceras == null || cabeceras.size() != 1) {
            throw noAutenticado();
        }

        String cabecera = cabeceras.get(0);
        if (cabecera == null
                || cabecera.length() <= 7
                || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw noAutenticado();
        }

        String token = cabecera.substring(7);
        if (token.isBlank()
                || token.chars().anyMatch(Character::isWhitespace)) {
            throw noAutenticado();
        }

        AutorizadorAgendas.IdentidadAutorizada identidad =
                autorizador.autenticar(cabecera)
                        .orElseThrow(AgendasHttpHandler::noAutenticado);

        if (!identidad.tieneRolAdmision()) {
            throw new ErrorHttp(
                    403, "PROHIBIDO", "Se requiere el rol ADMISION");
        }
    }

    private Map<String, String> leerConsulta(HttpExchange intercambio) {
        Map<String, String> datos = new HashMap<>();
        String consulta = intercambio.getRequestURI().getRawQuery();

        if (consulta == null) {
            return datos;
        }

        for (String parametro : consulta.split("&", -1)) {
            String[] partes = parametro.split("=", 2);
            if (partes.length != 2) {
                throw new IllegalArgumentException(
                        "Parámetro de consulta inválido");
            }

            String nombre = URLDecoder.decode(
                    partes[0], StandardCharsets.UTF_8);
            String valor = URLDecoder.decode(
                    partes[1], StandardCharsets.UTF_8);

            if (nombre.isBlank() || valor.isBlank()
                    || datos.putIfAbsent(nombre, valor) != null) {
                throw new IllegalArgumentException(
                        "Parámetro vacío o duplicado");
            }
        }

        return datos;
    }

    private String segmento(String valor) {
        String decodificado = URLDecoder.decode(
                valor.replace("+", "%2B"), StandardCharsets.UTF_8);

        if (decodificado.isBlank()
                || decodificado.contains("/")
                || decodificado.contains("\\")) {
            throw new IllegalArgumentException(
                    "Identificador de ruta inválido");
        }

        return decodificado;
    }

    private String leerCuerpo(HttpExchange intercambio) throws IOException {
        String tipo = intercambio.getRequestHeaders()
                .getFirst("Content-Type");

        if (tipo == null
                || !tipo.split(";", 2)[0].trim()
                        .equalsIgnoreCase("application/json")) {
            throw new ErrorHttp(
                    415, "TIPO_NO_ADMITIDO",
                    "Se requiere Content-Type application/json");
        }

        byte[] bytes = intercambio.getRequestBody()
                .readNBytes(MAX_CUERPO_BYTES + 1);

        if (bytes.length > MAX_CUERPO_BYTES) {
            throw new ErrorHttp(
                    413, "CUERPO_DEMASIADO_GRANDE",
                    "El cuerpo supera el tamaño permitido");
        }

        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException(
                    "El cuerpo debe estar codificado en UTF-8", error);
        }
    }

    private void responder(
            HttpExchange intercambio,
            int estado,
            String json) throws IOException {

        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);

        intercambio.getResponseHeaders().set(
                "Content-Type", "application/json; charset=utf-8");
        intercambio.getResponseHeaders().set(
                "Cache-Control", "no-store");
        intercambio.getResponseHeaders().set(
                "X-Content-Type-Options", "nosniff");

        intercambio.sendResponseHeaders(estado, cuerpo.length);

        try (var salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    private static ErrorHttp noEncontrado() {
        return new ErrorHttp(
                404, "NO_ENCONTRADO", "Ruta no encontrada");
    }

    private static ErrorHttp noAutenticado() {
        return new ErrorHttp(
                401, "NO_AUTENTICADO", "Se requiere una sesión válida");
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