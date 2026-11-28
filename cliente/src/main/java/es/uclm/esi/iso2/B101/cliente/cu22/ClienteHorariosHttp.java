package es.uclm.esi.iso2.B101.cliente.cu22;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Cliente HTTP para configurar horarios operativos de salas, equipos y médicos (CU22 / RF22).
 */
public final class ClienteHorariosHttp implements AutoCloseable {

    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteHorariosHttp(String urlBase, String token) {
        this.urlBase = validarUrlBase(urlBase);
        validarTexto(token, "token");
        if (token.chars().anyMatch(Character::isWhitespace) || token.indexOf(',') >= 0) {
            throw new IllegalArgumentException("El token no es válido");
        }
        this.token = token;
        this.clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ResultadoHttp crearHorario(
            String recursoId,
            String tipoRecurso,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            int duracionHuecoMinutos)
            throws IOException, InterruptedException {
        String cuerpo = serializarCuerpo(
                recursoId,
                tipoRecurso,
                diaSemana,
                horaInicio,
                horaFin,
                duracionHuecoMinutos);
        return enviarConCuerpo("POST", "api/horarios", cuerpo);
    }

    public ResultadoHttp listarHorarios(String recursoId, DayOfWeek diaSemana)
            throws IOException, InterruptedException {
        StringBuilder ruta = new StringBuilder("api/horarios");
        boolean tieneParam = false;
        if (recursoId != null && !recursoId.isBlank()) {
            ruta.append("?recursoId=").append(URLEncoder.encode(recursoId.trim(), StandardCharsets.UTF_8));
            tieneParam = true;
        }
        if (diaSemana != null) {
            ruta.append(tieneParam ? "&" : "?").append("diaSemana=").append(diaSemana.name());
        }
        return enviarGet(ruta.toString());
    }

    public ResultadoHttp obtenerHorario(String id) throws IOException, InterruptedException {
        String idValido = validarTexto(id, "id");
        return enviarGet("api/horarios/" + codificarSegmento(idValido));
    }

    public ResultadoHttp actualizarHorario(
            String id,
            String recursoId,
            String tipoRecurso,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            int duracionHuecoMinutos)
            throws IOException, InterruptedException {
        String idValido = validarTexto(id, "id");
        String cuerpo = serializarCuerpo(
                recursoId,
                tipoRecurso,
                diaSemana,
                horaInicio,
                horaFin,
                duracionHuecoMinutos);
        return enviarConCuerpo("PUT", "api/horarios/" + codificarSegmento(idValido), cuerpo);
    }

    public ResultadoHttp cambiarEstado(String id, boolean activo) throws IOException, InterruptedException {
        String idValido = validarTexto(id, "id");
        String cuerpo = "{\"activo\":" + activo + "}";
        return enviarConCuerpo("POST", "api/horarios/" + codificarSegmento(idValido) + "/estado", cuerpo);
    }

    @Override
    public void close() {
        clienteHttp.close();
    }

    private String serializarCuerpo(
            String recursoId,
            String tipoRecurso,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            int duracionHuecoMinutos) {
        String recursoValido = validarTexto(recursoId, "recursoId");
        String tipoValido = validarTexto(tipoRecurso, "tipoRecurso");
        Objects.requireNonNull(diaSemana, "diaSemana no puede ser nulo");
        Objects.requireNonNull(horaInicio, "horaInicio no puede ser nula");
        Objects.requireNonNull(horaFin, "horaFin no puede ser nula");
        if (!horaInicio.isBefore(horaFin)) {
            throw new IllegalArgumentException("horaInicio debe ser estrictamente anterior a horaFin");
        }
        if (duracionHuecoMinutos <= 0 || duracionHuecoMinutos > 24 * 60) {
            throw new IllegalArgumentException("duracionHuecoMinutos debe estar entre 1 y 1440");
        }
        return "{\"recursoId\":" + cadenaJson(recursoValido)
                + ",\"tipoRecurso\":" + cadenaJson(tipoValido)
                + ",\"diaSemana\":" + cadenaJson(diaSemana.name())
                + ",\"horaInicio\":" + cadenaJson(horaInicio.toString())
                + ",\"horaFin\":" + cadenaJson(horaFin.toString())
                + ",\"duracionHuecoMinutos\":" + duracionHuecoMinutos
                + '}';
    }

    private ResultadoHttp enviarGet(String ruta) throws IOException, InterruptedException {
        HttpRequest solicitud = HttpRequest.newBuilder(urlBase.resolve(ruta))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> respuesta = clienteHttp.send(
                solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new ResultadoHttp(respuesta.statusCode(), respuesta.body());
    }

    private ResultadoHttp enviarConCuerpo(String metodo, String ruta, String cuerpoJson)
            throws IOException, InterruptedException {
        HttpRequest solicitud = HttpRequest.newBuilder(urlBase.resolve(ruta))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + token)
                .method(metodo, HttpRequest.BodyPublishers.ofString(cuerpoJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> respuesta = clienteHttp.send(
                solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new ResultadoHttp(respuesta.statusCode(), respuesta.body());
    }

    private static String validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede ser nulo ni estar vacío");
        }
        return valor.trim();
    }

    private static String codificarSegmento(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String cadenaJson(String valor) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static URI validarUrlBase(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("La URL base no puede ser nula ni estar vacía");
        }
        URI uri;
        try {
            uri = URI.create(valor);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("La URL base no es válida");
        }
        String esquema = uri.getScheme();
        if (uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (!"http".equalsIgnoreCase(esquema) && !"https".equalsIgnoreCase(esquema))) {
            throw new IllegalArgumentException("La URL base debe ser una URL HTTP o HTTPS válida");
        }
        String baseNormalizada = uri.toASCIIString();
        if (!baseNormalizada.endsWith("/")) {
            baseNormalizada += "/";
        }
        return URI.create(baseNormalizada);
    }

    public record ResultadoHttp(int codigoHttp, String cuerpoJson) {
        public ResultadoHttp {
            if (codigoHttp < 100 || codigoHttp > 599) {
                throw new IllegalArgumentException("El código HTTP debe estar entre 100 y 599");
            }
            Objects.requireNonNull(cuerpoJson, "El cuerpo de respuesta no puede ser nulo");
        }

        public boolean esExito() {
            return codigoHttp >= 200 && codigoHttp < 300;
        }
    }
}
