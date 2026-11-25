package es.uclm.esi.iso2.B101.cliente.cu18;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

public final class ClienteListaEsperaHttp implements AutoCloseable {
    private static final String RUTA = "api/lista-espera";
    private static final Set<String> PRIORIDADES =
            Set.of("URGENTE", "PREFERENTE", "ORDINARIA");
    private static final char[] HEX =
            "0123456789abcdef".toCharArray();

    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteListaEsperaHttp(String urlBase, String token) {
        validarTexto(urlBase, "urlBase");
        validarTexto(token, "token");

        URI direccion = URI.create(urlBase);

        if (!("http".equalsIgnoreCase(direccion.getScheme())
                || "https".equalsIgnoreCase(direccion.getScheme()))
                || direccion.getHost() == null
                || direccion.getRawUserInfo() != null
                || direccion.getRawQuery() != null
                || direccion.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "urlBase debe ser una direccion HTTP o HTTPS valida");
        }

        if (token.chars().anyMatch(Character::isWhitespace)
                || token.indexOf(',') >= 0) {
            throw new IllegalArgumentException("El token no es valido");
        }

        this.urlBase = URI.create(
                urlBase.endsWith("/") ? urlBase : urlBase + "/");
        this.token = token;
        this.clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ResultadoHttp inscribir(
            String pacienteId,
            String medicoId,
            String tipoPrueba,
            String prioridad) throws IOException, InterruptedException {
        validarTexto(pacienteId, "pacienteId");
        validarTexto(medicoId, "medicoId");
        validarTexto(tipoPrueba, "tipoPrueba");
        validarPrioridad(prioridad);

        String cuerpo = "{\"pacienteId\":" + cadena(pacienteId)
                + ",\"medicoId\":" + cadena(medicoId)
                + ",\"tipoPrueba\":" + cadena(tipoPrueba)
                + ",\"prioridad\":" + cadena(prioridad)
                + "}";

        return enviar("POST", RUTA, cuerpo);
    }

    public ResultadoHttp consultarPendientes()
            throws IOException, InterruptedException {
        return enviar("GET", RUTA, null);
    }

    public ResultadoHttp consultarPendientes(
            String medicoId,
            String tipoPrueba) throws IOException, InterruptedException {
        validarTexto(medicoId, "medicoId");
        validarTexto(tipoPrueba, "tipoPrueba");

        return enviar(
                "GET",
                RUTA + "?medicoId=" + codificar(medicoId)
                        + "&tipoPrueba=" + codificar(tipoPrueba),
                null);
    }

    public ResultadoHttp consultarPorPaciente(String pacienteId)
            throws IOException, InterruptedException {
        validarTexto(pacienteId, "pacienteId");

        return enviar(
                "GET",
                RUTA + "?pacienteId=" + codificar(pacienteId),
                null);
    }

    public ResultadoHttp consultarEntrada(String entradaId)
            throws IOException, InterruptedException {
        return enviar("GET", rutaEntrada(entradaId), null);
    }

    public ResultadoHttp cambiarPrioridad(
            String entradaId,
            String prioridad) throws IOException, InterruptedException {
        String ruta = rutaEntrada(entradaId);
        validarPrioridad(prioridad);

        return enviar(
                "PUT",
                ruta + "/prioridad",
                "{\"prioridad\":" + cadena(prioridad) + "}");
    }

    public ResultadoHttp cancelar(String entradaId)
            throws IOException, InterruptedException {
        return enviar(
                "POST", rutaEntrada(entradaId) + "/cancelacion", null);
    }

    public ResultadoHttp asignarSiguiente(
            String medicoId,
            String tipoPrueba,
            LocalDateTime fechaHora,
            int duracionMinutos) throws IOException, InterruptedException {
        validarTexto(medicoId, "medicoId");
        validarTexto(tipoPrueba, "tipoPrueba");

        if (fechaHora == null) {
            throw new IllegalArgumentException("fechaHora es obligatoria");
        }

        if (duracionMinutos <= 0 || duracionMinutos > 1440) {
            throw new IllegalArgumentException(
                    "duracionMinutos debe estar entre 1 y 1440");
        }

        String cuerpo = "{\"medicoId\":" + cadena(medicoId)
                + ",\"tipoPrueba\":" + cadena(tipoPrueba)
                + ",\"fechaHora\":" + cadena(fechaHora.toString())
                + ",\"duracionMinutos\":" + duracionMinutos
                + "}";

        return enviar("POST", RUTA + "/asignaciones", cuerpo);
    }

    private ResultadoHttp enviar(
            String metodo,
            String ruta,
            String cuerpo) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder()
                .uri(urlBase.resolve(ruta))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);

        if (cuerpo != null) {
            peticion.header(
                    "Content-Type", "application/json; charset=utf-8");
        }

        peticion.method(
                metodo,
                cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(
                                cuerpo, StandardCharsets.UTF_8));

        HttpResponse<String> respuesta = clienteHttp.send(
                peticion.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        return new ResultadoHttp(
                respuesta.statusCode(), respuesta.body());
    }

    private static String rutaEntrada(String entradaId) {
        validarTexto(entradaId, "entradaId");

        if (entradaId.contains("/")
                || entradaId.contains("\\")
                || entradaId.equals(".")
                || entradaId.equals("..")
                || entradaId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("entradaId no es valido");
        }

        return RUTA + "/" + codificar(entradaId);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static void validarPrioridad(String prioridad) {
        if (prioridad == null || !PRIORIDADES.contains(prioridad)) {
            throw new IllegalArgumentException(
                    "La prioridad debe ser URGENTE, PREFERENTE u ORDINARIA");
        }
    }

    private static void validarTexto(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " no puede ser nulo ni estar vacio");
        }
    }

    private static String cadena(String valor) {
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
                        json.append("\\u")
                                .append(HEX[(caracter >>> 12) & 0xf])
                                .append(HEX[(caracter >>> 8) & 0xf])
                                .append(HEX[(caracter >>> 4) & 0xf])
                                .append(HEX[caracter & 0xf]);
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
                throw new IllegalArgumentException("Codigo HTTP invalido");
            }
            Objects.requireNonNull(
                    cuerpoJson, "cuerpoJson es obligatorio");
        }

        public boolean esExito() {
            return codigoHttp >= 200 && codigoHttp < 300;
        }
    }
}