package es.uclm.esi.iso2.B101.cliente.cu11;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class ClienteInformesPruebaHttp {
    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteInformesPruebaHttp(String urlBase, String token) {
        this.urlBase = validarUrlBase(urlBase);
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El token debe ser válido y no estar vacío");
        }
        this.token = token;
        this.clienteHttp = HttpClient.newHttpClient();
    }

    public ResultadoHttp crearInforme(String pruebaId, String hallazgos, String conclusion)
            throws IOException, InterruptedException {
        validarObligatorio(pruebaId, "pruebaId");
        validarObligatorio(hallazgos, "hallazgos");
        validarObligatorio(conclusion, "conclusion");
        String cuerpo = "{\"pruebaId\":" + cadena(pruebaId)
                + ",\"hallazgos\":" + cadena(hallazgos)
                + ",\"conclusion\":" + cadena(conclusion) + "}";
        return enviarJson("POST", "api/informes-prueba", cuerpo);
    }

    public ResultadoHttp consultarInformesPaciente() throws IOException, InterruptedException {
        return enviarJson("GET", "api/informes-prueba", null);
    }

    public ResultadoHttp consultarInformesMedico() throws IOException, InterruptedException {
        return enviarJson("GET", "api/informes-prueba/medico", null);
    }

    public ResultadoHttp consultarInforme(String informeId) throws IOException, InterruptedException {
        return enviarJson("GET", "api/informes-prueba/" + codificarSegmento(informeId), null);
    }

    public ResultadoHttp adjuntarImagen(String informeId, String tipoImagen, byte[] imagen)
            throws IOException, InterruptedException {
        validarObligatorio(informeId, "informeId");
        validarObligatorio(tipoImagen, "tipoImagen");
        Objects.requireNonNull(imagen, "imagen no puede ser nula");
        String cuerpo = "{\"tipoImagen\":" + cadena(tipoImagen)
                + ",\"imagenBase64\":" + cadena(Base64.getEncoder().encodeToString(imagen)) + "}";
        return enviarJson("POST", "api/informes-prueba/" + codificarSegmento(informeId) + "/imagen", cuerpo);
    }

    public ImagenHttp consultarImagen(String informeId) throws IOException, InterruptedException {
        HttpRequest solicitud = HttpRequest.newBuilder(
                        urlBase.resolve("api/informes-prueba/" + codificarSegmento(informeId) + "/imagen"))
                .header("Accept", "image/png, image/jpeg")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<byte[]> respuesta =
                clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofByteArray());
        return new ImagenHttp(
                respuesta.statusCode(),
                respuesta.headers().firstValue("Content-Type").orElse("application/octet-stream"),
                respuesta.body());
    }

    private ResultadoHttp enviarJson(String metodo, String ruta, String cuerpo)
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
        return new ResultadoHttp(
                respuesta.statusCode(),
                respuesta.headers().firstValue("Content-Type").orElse(""),
                respuesta.body());
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

    private static String codificarSegmento(String identificador) {
        validarObligatorio(identificador, "informeId");
        return URLEncoder.encode(identificador, StandardCharsets.UTF_8).replace("+", "%20");
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

    public record ResultadoHttp(int codigoHttp, String tipoContenido, String cuerpo) {
        public ResultadoHttp {
            if (codigoHttp < 100 || codigoHttp > 599) {
                throw new IllegalArgumentException("El código HTTP debe estar entre 100 y 599");
            }
            Objects.requireNonNull(tipoContenido, "tipoContenido no puede ser nulo");
            Objects.requireNonNull(cuerpo, "cuerpo no puede ser nulo");
        }

        public boolean esExito() {
            return codigoHttp >= 200 && codigoHttp < 300;
        }
    }

    public record ImagenHttp(int codigoHttp, String tipoContenido, byte[] contenido) {
        public ImagenHttp {
            if (codigoHttp < 100 || codigoHttp > 599) {
                throw new IllegalArgumentException("El código HTTP debe estar entre 100 y 599");
            }
            Objects.requireNonNull(tipoContenido, "tipoContenido no puede ser nulo");
            contenido = Objects.requireNonNull(contenido, "contenido no puede ser nulo").clone();
        }

        @Override
        public byte[] contenido() {
            return contenido.clone();
        }

        public boolean esExito() {
            return codigoHttp >= 200 && codigoHttp < 300;
        }
    }
}
