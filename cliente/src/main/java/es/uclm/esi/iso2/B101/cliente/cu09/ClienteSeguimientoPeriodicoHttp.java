package es.uclm.esi.iso2.B101.cliente.cu09;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;

public final class ClienteSeguimientoPeriodicoHttp {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final char[] HEX_MINUSCULAS = "0123456789abcdef".toCharArray();

    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteSeguimientoPeriodicoHttp(String urlBase, String token) {
        this.urlBase = validarUrlBase(urlBase);
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El token debe ser válido y no estar vacío");
        }
        this.token = token;
        this.clienteHttp = HttpClient.newHttpClient();
    }

    public ResultadoHttp registrarPrescripcion(DatosPrescripcion datos) throws IOException, InterruptedException {
        Objects.requireNonNull(datos, "datos no pueden ser nulos");
        String cuerpo = "{\"pacienteId\":" + cadena(datos.pacienteId())
                + ",\"tipoPrueba\":" + cadena(datos.tipoPrueba())
                + ",\"motivoClinico\":" + cadena(datos.motivoClinico())
                + ",\"periodicidadMeses\":" + datos.periodicidadMeses()
                + ",\"fechaPrimeraRevision\":" + cadena(datos.fechaPrimeraRevision().toString())
                + ",\"prioridad\":" + cadena(datos.prioridad()) + "}";
        HttpRequest solicitud = solicitud("api/prescripciones-seguimiento")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        return enviar(solicitud);
    }

    public ResultadoHttp consultarPrescripciones() throws IOException, InterruptedException {
        return enviar(solicitud("api/prescripciones-seguimiento").GET().build());
    }

    public ResultadoHttp consultarDetalle(String prescripcionId) throws IOException, InterruptedException {
        return enviar(solicitud(
                        "api/prescripciones-seguimiento/" + codificarSegmento(prescripcionId))
                .GET()
                .build());
    }

    public ResultadoHttp consultarNecesidadesCita(String prescripcionId) throws IOException, InterruptedException {
        return enviar(solicitud(
                        "api/prescripciones-seguimiento/" + codificarSegmento(prescripcionId) + "/necesidades-cita")
                .GET()
                .build());
    }

    private HttpRequest.Builder solicitud(String ruta) {
        return HttpRequest.newBuilder(urlBase.resolve(ruta))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);
    }

    private ResultadoHttp enviar(HttpRequest solicitud) throws IOException, InterruptedException {
        try {
            HttpResponse<String> respuesta =
                    clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ResultadoHttp(respuesta.statusCode(), respuesta.body());
        } catch (IOException error) {
            throw new IOException("No se pudo conectar con el servidor", error);
        }
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

    private static String codificarSegmento(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException("prescripcionId no puede ser nulo ni estar vacío");
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
                        .append(HEX[valor >>> 4])
                        .append(HEX[valor & 0x0f]);
            }
        }
        return codificado.toString();
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
                                .append(HEX_MINUSCULAS[(caracter >>> 12) & 0x0f])
                                .append(HEX_MINUSCULAS[(caracter >>> 8) & 0x0f])
                                .append(HEX_MINUSCULAS[(caracter >>> 4) & 0x0f])
                                .append(HEX_MINUSCULAS[caracter & 0x0f]);
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
            String motivoClinico,
            int periodicidadMeses,
            LocalDate fechaPrimeraRevision,
            String prioridad) {
        public DatosPrescripcion {
            validarObligatorio(pacienteId, "pacienteId");
            validarObligatorio(tipoPrueba, "tipoPrueba");
            validarObligatorio(motivoClinico, "motivoClinico");
            if (periodicidadMeses <= 0) {
                throw new IllegalArgumentException("periodicidadMeses debe ser positiva");
            }
            if (fechaPrimeraRevision == null) {
                throw new IllegalArgumentException("fechaPrimeraRevision no puede ser nula");
            }
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
