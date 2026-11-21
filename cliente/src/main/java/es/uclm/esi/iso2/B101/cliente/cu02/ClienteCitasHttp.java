package es.uclm.esi.iso2.B101.cliente.cu02;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

public final class ClienteCitasHttp {
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteCitasHttp(String urlBase, String token) {
        this.urlBase = validarUrlBase(urlBase);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token no puede ser nulo ni estar vacío");
        }
        this.token = token;
        this.clienteHttp = HttpClient.newHttpClient();
    }

    public ResultadoHttp consultarCitas() throws IOException, InterruptedException {
        return enviar("GET", "api/citas", null);
    }

    public ResultadoHttp confirmarCita(String citaId) throws IOException, InterruptedException {
        return enviar("POST", "api/citas/" + codificarSegmento(citaId) + "/confirmacion", null);
    }

    public ResultadoHttp solicitarCambioFecha(String citaId, LocalDateTime nuevaFechaHora)
            throws IOException, InterruptedException {
        if (nuevaFechaHora == null) {
            throw new IllegalArgumentException("nuevaFechaHora no puede ser nula");
        }
        String cuerpo = "{\"nuevaFechaHora\":\"" + nuevaFechaHora + "\"}";
        return enviar("POST", "api/citas/" + codificarSegmento(citaId) + "/solicitudes/cambio", cuerpo);
    }

    public ResultadoHttp solicitarCancelacion(String citaId) throws IOException, InterruptedException {
        return enviar("POST", "api/citas/" + codificarSegmento(citaId) + "/solicitudes/cancelacion", null);
    }

    public ResultadoHttp consultarSolicitudes() throws IOException, InterruptedException {
        return enviar("GET", "api/solicitudes-cita", null);
    }

    private ResultadoHttp enviar(String metodo, String ruta, String cuerpo)
            throws IOException, InterruptedException {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(urlBase.resolve(ruta))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);

        HttpRequest.BodyPublisher contenido = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8);
        if (cuerpo != null) {
            solicitud.header("Content-Type", "application/json; charset=utf-8");
        }

        HttpResponse<String> respuesta = clienteHttp.send(
                solicitud.method(metodo, contenido).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new ResultadoHttp(respuesta.statusCode(), respuesta.body());
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

    private static String codificarSegmento(String identificador) {
        Objects.requireNonNull(identificador, "citaId no puede ser nulo");
        if (identificador.isBlank()) {
            throw new IllegalArgumentException("citaId no puede estar vacío");
        }

        StringBuilder codificado = new StringBuilder();
        for (byte byteUtf8 : identificador.getBytes(StandardCharsets.UTF_8)) {
            int valor = byteUtf8 & 0xff;
            if ((valor >= 'a' && valor <= 'z')
                    || (valor >= 'A' && valor <= 'Z')
                    || (valor >= '0' && valor <= '9')
                    || valor == '-' || valor == '_' || valor == '~') {
                codificado.append((char) valor);
            } else {
                codificado.append('%')
                        .append(HEX_DIGITS[valor >>> 4])
                        .append(HEX_DIGITS[valor & 0x0f]);
            }
        }
        return codificado.toString();
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
