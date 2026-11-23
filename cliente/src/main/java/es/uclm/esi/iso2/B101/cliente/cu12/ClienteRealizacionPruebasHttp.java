package es.uclm.esi.iso2.B101.cliente.cu12;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ClienteRealizacionPruebasHttp {
    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteRealizacionPruebasHttp(String urlBase, String token) {
        this.urlBase = validarUrlBase(urlBase);
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El token debe ser válido y no estar vacío");
        }
        this.token = token;
        this.clienteHttp = HttpClient.newHttpClient();
    }

    public ResultadoHttp registrarRealizacion(String citaId) throws IOException, InterruptedException {
        validarObligatorio(citaId, "citaId");
        String cuerpo = "{\"citaId\":" + cadena(citaId) + ",\"resultado\":\"REALIZADA\"}";
        return enviar(cuerpo);
    }

    public ResultadoHttp registrarIncidenciaRealizacion(String citaId, String descripcion)
            throws IOException, InterruptedException {
        validarObligatorio(citaId, "citaId");
        validarObligatorio(descripcion, "descripcion");
        String cuerpo = "{\"citaId\":" + cadena(citaId)
                + ",\"resultado\":\"INCIDENCIA\",\"descripcion\":" + cadena(descripcion) + "}";
        return enviar(cuerpo);
    }

    private ResultadoHttp enviar(String cuerpo) throws IOException, InterruptedException {
        HttpRequest solicitud = HttpRequest.newBuilder(urlBase.resolve("api/realizaciones-prueba"))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> respuesta = clienteHttp.send(
                solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new ResultadoHttp(respuesta.statusCode(), respuesta.body());
    }

    private static String cadena(String valor) {
        StringBuilder json = new StringBuilder("\"");
        for (int indice = 0; indice < valor.length(); indice++) {
            char caracter = valor.charAt(indice);
            switch (caracter) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (caracter < 0x20 || Character.isSurrogate(caracter)) {
                        json.append(String.format("\\u%04x", (int) caracter));
                    } else {
                        json.append(caracter);
                    }
                }
            }
        }
        return json.append('"').toString();
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
        String normalizada = uri.toASCIIString();
        if (!normalizada.endsWith("/")) {
            normalizada += "/";
        }
        return URI.create(normalizada);
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
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
