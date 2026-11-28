package es.uclm.esi.iso2.B101.cliente.cu21;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Cliente HTTP para configurar protocolos clínicos y técnicos (CU21 / RF21).
 */
public final class ClienteProtocolosHttp implements AutoCloseable {

    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteProtocolosHttp(String urlBase, String token) {
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

    public ResultadoHttp crearProtocolo(
            String codigo,
            String nombre,
            String tipoPruebaId,
            String descripcion,
            String pasosPreparacion,
            String requisitosSeguridad,
            boolean usoContraste)
            throws IOException, InterruptedException {
        String cuerpo = serializarCuerpo(
                codigo,
                nombre,
                tipoPruebaId,
                descripcion,
                pasosPreparacion,
                requisitosSeguridad,
                usoContraste);
        return enviarConCuerpo("POST", "api/protocolos", cuerpo);
    }

    public ResultadoHttp listarProtocolos(String tipoPruebaId) throws IOException, InterruptedException {
        String ruta = (tipoPruebaId == null || tipoPruebaId.isBlank())
                ? "api/protocolos"
                : "api/protocolos?tipoPruebaId=" + URLEncoder.encode(tipoPruebaId.trim(), StandardCharsets.UTF_8);
        return enviarGet(ruta);
    }

    public ResultadoHttp obtenerProtocolo(String id) throws IOException, InterruptedException {
        String idValido = validarTexto(id, "id");
        return enviarGet("api/protocolos/" + codificarSegmento(idValido));
    }

    public ResultadoHttp actualizarProtocolo(
            String id,
            String codigo,
            String nombre,
            String tipoPruebaId,
            String descripcion,
            String pasosPreparacion,
            String requisitosSeguridad,
            boolean usoContraste)
            throws IOException, InterruptedException {
        String idValido = validarTexto(id, "id");
        String cuerpo = serializarCuerpo(
                codigo,
                nombre,
                tipoPruebaId,
                descripcion,
                pasosPreparacion,
                requisitosSeguridad,
                usoContraste);
        return enviarConCuerpo("PUT", "api/protocolos/" + codificarSegmento(idValido), cuerpo);
    }

    public ResultadoHttp cambiarEstado(String id, boolean activo) throws IOException, InterruptedException {
        String idValido = validarTexto(id, "id");
        String cuerpo = "{\"activo\":" + activo + "}";
        return enviarConCuerpo("POST", "api/protocolos/" + codificarSegmento(idValido) + "/estado", cuerpo);
    }

    @Override
    public void close() {
        clienteHttp.close();
    }

    private String serializarCuerpo(
            String codigo,
            String nombre,
            String tipoPruebaId,
            String descripcion,
            String pasosPreparacion,
            String requisitosSeguridad,
            boolean usoContraste) {
        return "{\"codigo\":" + cadenaJson(validarTexto(codigo, "codigo"))
                + ",\"nombre\":" + cadenaJson(validarTexto(nombre, "nombre"))
                + ",\"tipoPruebaId\":" + cadenaJson(validarTexto(tipoPruebaId, "tipoPruebaId"))
                + ",\"descripcion\":" + cadenaJson(validarTexto(descripcion, "descripcion"))
                + ",\"pasosPreparacion\":" + cadenaJson(validarTexto(pasosPreparacion, "pasosPreparacion"))
                + ",\"requisitosSeguridad\":" + cadenaJson(validarTexto(requisitosSeguridad, "requisitosSeguridad"))
                + ",\"usoContraste\":" + usoContraste
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
