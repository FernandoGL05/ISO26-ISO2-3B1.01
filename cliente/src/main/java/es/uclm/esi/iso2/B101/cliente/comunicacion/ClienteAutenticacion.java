package es.uclm.esi.iso2.B101.cliente.comunicacion;

import es.uclm.esi.iso2.B101.cliente.modelo.RespuestaLogin;
import es.uclm.esi.iso2.B101.cliente.modelo.SesionCliente;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente HTTP que comunica la aplicación cliente con la API de autenticación del servidor (CU01).
 */
public class ClienteAutenticacion {

    private final String baseUrl;
    private final HttpClient httpClient;

    public ClienteAutenticacion(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public ClienteAutenticacion(String baseUrl, HttpClient httpClient) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
    }

    public RespuestaLogin iniciarSesion(String username, String password) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("password", password);
        return enviarPostLogin("/api/auth/login", payload);
    }

    public RespuestaLogin iniciarSesionSSO(String ssoToken) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("ssoToken", ssoToken);
        return enviarPostLogin("/api/auth/sso", payload);
    }

    public Optional<SesionCliente> validarSesion(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/session"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            Map<String, String> datos = JsonClienteUtil.parsearObjetoPlano(response.body());
            return Optional.of(construirSesion(datos));
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    public boolean cerrarSesion(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/logout"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private RespuestaLogin enviarPostLogin(String ruta, Map<String, String> payload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + ruta))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(JsonClienteUtil.aJson(payload), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, String> datos = JsonClienteUtil.parsearObjetoPlano(response.body());
            boolean exito = response.statusCode() == 200 && "true".equalsIgnoreCase(datos.get("exito"));
            if (exito) {
                return RespuestaLogin.correcta(
                        datos.getOrDefault("mensaje", "Inicio de sesión correcto."),
                        construirSesion(datos)
                );
            }
            return RespuestaLogin.error(
                    datos.getOrDefault("codigoError", "ERROR_SERVIDOR"),
                    datos.getOrDefault("mensaje", "No se pudo completar el inicio de sesión.")
            );
        } catch (IOException e) {
            return RespuestaLogin.error("ERROR_CONEXION", "No se pudo conectar con el servidor: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RespuestaLogin.error("ERROR_CONEXION", "La petición de inicio de sesión fue interrumpida.");
        }
    }

    private SesionCliente construirSesion(Map<String, String> datos) {
        return new SesionCliente(
                datos.get("token"),
                datos.get("usuarioId"),
                datos.get("username"),
                datos.get("nombreCompleto"),
                datos.get("rol"),
                datos.get("expiraEn"),
                datos.get("origenAutenticacion")
        );
    }
}
