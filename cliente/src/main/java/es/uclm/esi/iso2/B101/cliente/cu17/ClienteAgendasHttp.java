package es.uclm.esi.iso2.B101.cliente.cu17;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public final class ClienteAgendasHttp implements AutoCloseable {
    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteAgendasHttp(String urlBase, String token) {
        this.urlBase = validarUrlBase(urlBase);

        if (token == null || token.isBlank()
                || token.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "El token no puede estar vacío ni contener espacios");
        }

        this.token = token;
        this.clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ResultadoHttp crearAgenda(String medicoId, LocalDate fecha)
            throws IOException, InterruptedException {

        validarIdentificador(medicoId);
        Objects.requireNonNull(fecha, "La fecha no puede ser nula");

        String cuerpo = "{\"medicoId\":" + textoJson(medicoId)
                + ",\"fecha\":" + textoJson(fecha.toString()) + "}";

        return enviar("POST", "api/agendas", cuerpo);
    }

    public ResultadoHttp consultarAgenda(String agendaId)
            throws IOException, InterruptedException {

        return enviar("GET", rutaAgenda(agendaId), null);
    }

    public ResultadoHttp consultarAgendasPorFecha(LocalDate fecha)
            throws IOException, InterruptedException {

        Objects.requireNonNull(fecha, "La fecha no puede ser nula");

        return enviar(
                "GET", "api/agendas?fecha=" + fecha, null);
    }

    public ResultadoHttp consultarAgendaPorMedicoYFecha(
            String medicoId, LocalDate fecha)
            throws IOException, InterruptedException {

        validarIdentificador(medicoId);
        Objects.requireNonNull(fecha, "La fecha no puede ser nula");

        return enviar(
                "GET",
                "api/agendas?medicoId=" + codificar(medicoId)
                        + "&fecha=" + fecha,
                null);
    }

    public ResultadoHttp agregarHueco(
            String agendaId, LocalTime inicio, LocalTime fin)
            throws IOException, InterruptedException {

        return enviar(
                "POST",
                rutaAgenda(agendaId) + "/huecos",
                cuerpoHorario(inicio, fin));
    }

    public ResultadoHttp consultarHuecos(String agendaId)
            throws IOException, InterruptedException {

        return enviar(
                "GET", rutaAgenda(agendaId) + "/huecos", null);
    }

    public ResultadoHttp modificarHueco(
            String agendaId,
            String huecoId,
            LocalTime inicio,
            LocalTime fin)
            throws IOException, InterruptedException {

        return enviar(
                "PUT",
                rutaAgenda(agendaId) + "/huecos/" + segmento(huecoId),
                cuerpoHorario(inicio, fin));
    }

    public ResultadoHttp eliminarHueco(
            String agendaId, String huecoId)
            throws IOException, InterruptedException {

        return enviar(
                "DELETE",
                rutaAgenda(agendaId) + "/huecos/" + segmento(huecoId),
                null);
    }

    private ResultadoHttp enviar(
            String metodo, String ruta, String cuerpo)
            throws IOException, InterruptedException {

        HttpRequest.Builder peticion =
                HttpRequest.newBuilder(urlBase.resolve(ruta))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + token);

        HttpRequest.BodyPublisher contenido =
                HttpRequest.BodyPublishers.noBody();

        if (cuerpo != null) {
            peticion.header(
                    "Content-Type", "application/json; charset=utf-8");

            contenido = HttpRequest.BodyPublishers.ofString(
                    cuerpo, StandardCharsets.UTF_8);
        }

        HttpResponse<String> respuesta = clienteHttp.send(
                peticion.method(metodo, contenido).build(),
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8));

        return new ResultadoHttp(
                respuesta.statusCode(), respuesta.body());
    }

    private static String rutaAgenda(String agendaId) {
        return "api/agendas/" + segmento(agendaId);
    }

    private static String segmento(String id) {
        validarIdentificador(id);

        if (id.contains("/") || id.contains("\\")
                || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException(
                    "Identificador de ruta inválido");
        }

        return codificar(id);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static void validarIdentificador(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador no puede ser nulo ni estar vacío");
        }
    }

    private static String cuerpoHorario(
            LocalTime inicio, LocalTime fin) {

        if (inicio == null || fin == null || !inicio.isBefore(fin)) {
            throw new IllegalArgumentException(
                    "Se requiere una hora inicial anterior a la final");
        }

        return "{\"horaInicio\":" + textoJson(inicio.toString())
                + ",\"horaFin\":" + textoJson(fin.toString()) + "}";
    }

    private static URI validarUrlBase(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "La URL base no puede estar vacía");
        }

        URI uri = URI.create(valor);
        String esquema = uri.getScheme();

        if (uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (!"http".equalsIgnoreCase(esquema)
                && !"https".equalsIgnoreCase(esquema))) {
            throw new IllegalArgumentException(
                    "Se requiere una URL HTTP o HTTPS válida");
        }

        String base = uri.toASCIIString();
        return URI.create(base.endsWith("/") ? base : base + "/");
    }

    private static String textoJson(String valor) {
        StringBuilder json = new StringBuilder("\"");

        for (int i = 0; i < valor.length(); i++) {
            char caracter = valor.charAt(i);

            switch (caracter) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (caracter < 0x20
                            || Character.isSurrogate(caracter)) {
                        json.append(String.format(
                                "\\u%04x", (int) caracter));
                    } else {
                        json.append(caracter);
                    }
                }
            }
        }

        return json.append('"').toString();
    }

    @Override
    public void close() {
        clienteHttp.close();
    }

    public record ResultadoHttp(int codigoHttp, String cuerpoJson) {
        public ResultadoHttp {
            if (codigoHttp < 100 || codigoHttp > 599) {
                throw new IllegalArgumentException(
                        "Código HTTP fuera de rango");
            }
            Objects.requireNonNull(cuerpoJson);
        }

        public boolean esExito() {
            return codigoHttp >= 200 && codigoHttp < 300;
        }
    }
}