package es.uclm.esi.iso2.B101.cliente.cu10;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

public final class ClienteAgendasHttp {
    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteAgendasHttp(String urlBase, String token) {
        this.urlBase = validarUrlBase(urlBase);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token no puede ser nulo ni estar vacío");
        }
        this.token = token;
        this.clienteHttp = HttpClient.newHttpClient();
    }

    public ResultadoHttp consultarDisponibilidad(
            String tipoPrueba, LocalDateTime desde, LocalDateTime hasta, int duracionMinutos)
            throws IOException, InterruptedException {
        if (tipoPrueba == null || tipoPrueba.isBlank()) {
            throw new IllegalArgumentException("tipoPrueba no puede ser nulo ni estar vacío");
        }
        if (desde == null || hasta == null || !desde.isBefore(hasta)
                || !desde.toLocalDate().equals(hasta.toLocalDate())) {
            throw new IllegalArgumentException("El intervalo debe ser válido y pertenecer a un único día");
        }
        if (duracionMinutos <= 0 || duracionMinutos > 24 * 60) {
            throw new IllegalArgumentException("duracionMinutos debe estar entre 1 y 1440");
        }

        String ruta = "api/agendas/disponibilidad?tipoPrueba=" + codificar(tipoPrueba)
                + "&desde=" + codificar(desde.toString())
                + "&hasta=" + codificar(hasta.toString())
                + "&duracionMinutos=" + duracionMinutos;
        HttpRequest solicitud = HttpRequest.newBuilder(urlBase.resolve(ruta))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> respuesta = clienteHttp.send(
                solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new ResultadoHttp(respuesta.statusCode(), respuesta.body());
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
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
