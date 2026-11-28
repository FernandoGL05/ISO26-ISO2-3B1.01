package es.uclm.esi.iso2.B101.cliente.cu20;

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
 * Cliente HTTP para configurar el catálogo de tipos de prueba (CU20 / RF20).
 */
public final class ClienteTiposPruebaHttp implements AutoCloseable {

    private final URI urlBase;
    private final String token;
    private final HttpClient clienteHttp;

    public ClienteTiposPruebaHttp(String urlBase, String token) {
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

    public ResultadoHttp crearTipoPrueba(
            String nombre,
            String modalidad,
            String equipoRequerido,
            String salaRequerida,
            int duracionEstimadaMinutos,
            String preparacionPrevia,
            boolean requiereContraste,
            boolean requiereConsentimientoInformado,
            String requisitosSeguridad,
            String prioridad,
            boolean admiteSeguimientoPeriodico)
            throws IOException, InterruptedException {
        String cuerpo = serializarCuerpo(
                nombre,
                modalidad,
                equipoRequerido,
                salaRequerida,
                duracionEstimadaMinutos,
                preparacionPrevia,
                requiereContraste,
                requiereConsentimientoInformado,
                requisitosSeguridad,
                prioridad,
                admiteSeguimientoPeriodico);
        return enviarConCuerpo("POST", "api/tipos-prueba", cuerpo);
    }

    public ResultadoHttp listarTiposPrueba(Boolean soloActivos) throws IOException, InterruptedException {
        String ruta = soloActivos == null
                ? "api/tipos-prueba"
                : "api/tipos-prueba?soloActivos=" + soloActivos;
        return enviarGet(ruta);
    }

    public ResultadoHttp obtenerTipoPrueba(String id) throws IOException, InterruptedException {
        String idValido = validarTexto(id, "id");
        return enviarGet("api/tipos-prueba/" + codificarSegmento(idValido));
    }

    public ResultadoHttp actualizarTipoPrueba(
            String id,
            String nombre,
            String modalidad,
            String equipoRequerido,
            String salaRequerida,
            int duracionEstimadaMinutos,
            String preparacionPrevia,
            boolean requiereContraste,
            boolean requiereConsentimientoInformado,
            String requisitosSeguridad,
            String prioridad,
            boolean admiteSeguimientoPeriodico)
            throws IOException, InterruptedException {
        String idValido = validarTexto(id, "id");
        String cuerpo = serializarCuerpo(
                nombre,
                modalidad,
                equipoRequerido,
                salaRequerida,
                duracionEstimadaMinutos,
                preparacionPrevia,
                requiereContraste,
                requiereConsentimientoInformado,
                requisitosSeguridad,
                prioridad,
                admiteSeguimientoPeriodico);
        return enviarConCuerpo("PUT", "api/tipos-prueba/" + codificarSegmento(idValido), cuerpo);
    }

    public ResultadoHttp cambiarEstado(String id, boolean activo) throws IOException, InterruptedException {
        String idValido = validarTexto(id, "id");
        String cuerpo = "{\"activo\":" + activo + "}";
        return enviarConCuerpo("POST", "api/tipos-prueba/" + codificarSegmento(idValido) + "/estado", cuerpo);
    }

    @Override
    public void close() {
        clienteHttp.close();
    }

    private String serializarCuerpo(
            String nombre,
            String modalidad,
            String equipoRequerido,
            String salaRequerida,
            int duracionEstimadaMinutos,
            String preparacionPrevia,
            boolean requiereContraste,
            boolean requiereConsentimientoInformado,
            String requisitosSeguridad,
            String prioridad,
            boolean admiteSeguimientoPeriodico) {
        String nombreValido = validarTexto(nombre, "nombre");
        String modalidadValida = validarTexto(modalidad, "modalidad");
        String equipoValido = validarTexto(equipoRequerido, "equipoRequerido");
        String salaValida = validarTexto(salaRequerida, "salaRequerida");
        if (duracionEstimadaMinutos <= 0 || duracionEstimadaMinutos > 24 * 60) {
            throw new IllegalArgumentException("duracionEstimadaMinutos debe estar entre 1 y 1440");
        }
        String prepValida = validarTexto(preparacionPrevia, "preparacionPrevia");
        if (requiereContraste && !requiereConsentimientoInformado) {
            throw new IllegalArgumentException(
                    "Si la prueba requiere contraste debe requerir consentimiento informado");
        }
        String seguridadValida = validarTexto(requisitosSeguridad, "requisitosSeguridad");
        String prioridadValida = validarTexto(prioridad, "prioridad");

        return "{\"nombre\":" + cadenaJson(nombreValido)
                + ",\"modalidad\":" + cadenaJson(modalidadValida)
                + ",\"equipoRequerido\":" + cadenaJson(equipoValido)
                + ",\"salaRequerida\":" + cadenaJson(salaValida)
                + ",\"duracionEstimadaMinutos\":" + duracionEstimadaMinutos
                + ",\"preparacionPrevia\":" + cadenaJson(prepValida)
                + ",\"requiereContraste\":" + requiereContraste
                + ",\"requiereConsentimientoInformado\":" + requiereConsentimientoInformado
                + ",\"requisitosSeguridad\":" + cadenaJson(seguridadValida)
                + ",\"prioridad\":" + cadenaJson(prioridadValida)
                + ",\"admiteSeguimientoPeriodico\":" + admiteSeguimientoPeriodico
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
