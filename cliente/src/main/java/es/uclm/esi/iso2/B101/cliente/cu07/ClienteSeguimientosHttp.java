package es.uclm.esi.iso2.B101.cliente.cu07;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ClienteSeguimientosHttp {
    private static final char[] DIGITOS_HEXADECIMALES = "0123456789ABCDEF".toCharArray();

    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteSeguimientosHttp(String urlBase, String token) {
        this.urlBase = validarUrlBase(urlBase);
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El token debe ser válido y no estar vacío");
        }
        this.token = token;
        this.clienteHttp = HttpClient.newHttpClient();
    }

    public ResultadoHttp consultarSeguimientos() throws IOException, InterruptedException {
        return enviar("api/seguimientos");
    }

    public ResultadoHttp consultarDetalle(String seguimientoId) throws IOException, InterruptedException {
        return enviar("api/seguimientos/" + codificarSegmento(seguimientoId));
    }

    private ResultadoHttp enviar(String ruta) throws IOException, InterruptedException {
        HttpRequest solicitud = HttpRequest.newBuilder(urlBase.resolve(ruta))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IOException("No se pudo conectar con el servidor", error);
        }
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
        String normalizada = uri.toASCIIString();
        if (!normalizada.endsWith("/")) {
            normalizada += "/";
        }
        return URI.create(normalizada);
    }

    private static String codificarSegmento(String identificador) {
        Objects.requireNonNull(identificador, "seguimientoId no puede ser nulo");
        if (identificador.isBlank()) {
            throw new IllegalArgumentException("seguimientoId no puede estar vacío");
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
                        .append(DIGITOS_HEXADECIMALES[valor >>> 4])
                        .append(DIGITOS_HEXADECIMALES[valor & 0x0f]);
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
