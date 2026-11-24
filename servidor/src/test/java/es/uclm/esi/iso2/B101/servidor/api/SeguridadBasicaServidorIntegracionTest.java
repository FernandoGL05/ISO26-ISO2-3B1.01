package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SeguridadBasicaServidorIntegracionTest {
    private final HttpClient clienteHttp = HttpClient.newHttpClient();
    private ServidorHTTP servidor;

    @AfterEach
    void detenerServidor() {
        if (servidor != null) {
            servidor.detener();
        }
    }

    @Test
    void limitaCuerposDeLoginYSsoSinAlterarElLoginValido() throws Exception {
        iniciarServidor(false);
        assertTrue(servidor.getDireccionEnlace().getAddress().isLoopbackAddress());
        String cuerpoDemasiadoGrande = "x".repeat(ServidorHTTP.MAX_CUERPO_AUTENTICACION_BYTES + 1);

        assertEquals(413, enviar("/api/auth/login", cuerpoDemasiadoGrande).statusCode());
        assertEquals(413, enviar("/api/auth/sso", cuerpoDemasiadoGrande).statusCode());
        assertEquals(
                200,
                enviar("/api/auth/login", "{\"username\":\"paciente1\",\"password\":\"paciente123\"}")
                        .statusCode());
    }

    @Test
    void soloElModoDemostracionAceptaElProveedorSsoSimulado() throws Exception {
        iniciarServidor(false);
        assertEquals(401, enviar("/api/auth/sso", "{\"ssoToken\":\"sso-sescam-paciente1\"}").statusCode());

        servidor.detener();
        iniciarServidor(true);
        HttpResponse<String> loginSso =
                enviar("/api/auth/sso", "{\"ssoToken\":\"sso-sescam-paciente1\"}");

        assertEquals(200, loginSso.statusCode());
        assertTrue(loginSso.body().contains("\"rol\":\"PACIENTE\""));
        assertTrue(loginSso.body().contains("\"token\":"));
    }

    private void iniciarServidor(boolean modoDemostracion) throws IOException {
        servidor = AplicacionServidor.crearServidor(0, modoDemostracion);
        servidor.iniciar();
        assertTrue(servidor.getDireccionEnlace().getAddress().isLoopbackAddress());
    }

    private HttpResponse<String> enviar(String ruta, String cuerpo) throws Exception {
        HttpRequest solicitud = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + servidor.getPuerto() + ruta))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        return clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
