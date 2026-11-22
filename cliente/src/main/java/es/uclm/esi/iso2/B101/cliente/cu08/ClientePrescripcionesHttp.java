package es.uclm.esi.iso2.B101.cliente.cu08;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ClientePrescripcionesHttp {
    private static final char[] DIGITOS_HEXADECIMALES = "0123456789abcdef".toCharArray();

    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClientePrescripcionesHttp(String urlBase, String token) {
        this.urlBase = validarUrlBase(urlBase);
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El token debe ser válido y no estar vacío");
        }
        this.token = token;
        this.clienteHttp = HttpClient.newHttpClient();
    }

    public ResultadoHttp prescribir(DatosPrescripcion datos) throws IOException, InterruptedException {
        Objects.requireNonNull(datos, "datos no pueden ser nulos");
        String cuerpo = "{\"pacienteId\":" + cadena(datos.pacienteId())
                + ",\"tipoPrueba\":" + cadena(datos.tipoPrueba())
                + ",\"indicacionClinica\":" + cadena(datos.indicacionClinica())
                + ",\"justificacion\":" + cadena(datos.justificacion())
                + ",\"prioridad\":" + cadena(datos.prioridad()) + "}";
        HttpRequest solicitud = solicitud("api/prescripciones-pruebas")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        return enviar(solicitud);
    }

    public ResultadoHttp consultarSolicitudes() throws IOException, InterruptedException {
        return enviar(solicitud("api/prescripciones-pruebas").GET().build());
    }

    public ResultadoHttp consultarDetalle(String id) throws IOException, InterruptedException {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser nulo ni estar vacío");
        }
        String idCodificado = URLEncoder.encode(id, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace(".", "%2E");
        return enviar(solicitud("api/prescripciones-pruebas/" + idCodificado).GET().build());
    }

    private HttpRequest.Builder solicitud(String ruta) {
        return HttpRequest.newBuilder(urlBase.resolve(ruta))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);
    }

    private ResultadoHttp enviar(HttpRequest solicitud) throws IOException, InterruptedException {
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
            throw new IllegalArgumentException("La URL base no es válida", error);
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
                        json.append("\\u")
                                .append(DIGITOS_HEXADECIMALES[(caracter >>> 12) & 0x0f])
                                .append(DIGITOS_HEXADECIMALES[(caracter >>> 8) & 0x0f])
                                .append(DIGITOS_HEXADECIMALES[(caracter >>> 4) & 0x0f])
                                .append(DIGITOS_HEXADECIMALES[caracter & 0x0f]);
                    } else {
                        json.append(caracter);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    public record DatosPrescripcion(
            String pacienteId,
            String tipoPrueba,
            String indicacionClinica,
            String justificacion,
            String prioridad) {
        public DatosPrescripcion {
            validarObligatorio(pacienteId, "pacienteId");
            validarObligatorio(tipoPrueba, "tipoPrueba");
            validarObligatorio(indicacionClinica, "indicacionClinica");
            validarObligatorio(justificacion, "justificacion");
            validarObligatorio(prioridad, "prioridad");
            if (!"URGENTE".equals(prioridad) && !"PREFERENTE".equals(prioridad) && !"ORDINARIA".equals(prioridad)) {
                throw new IllegalArgumentException("prioridad no válida");
            }
        }

        private static void validarObligatorio(String valor, String nombre) {
            if (valor == null || valor.isBlank()) {
                throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
            }
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
