package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.*;

import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSOSimulado;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAccesoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSesionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioUsuariosMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.GeneradorTokens;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServidorHTTPTest {

    private ServidorHTTP servidor;
    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        GestorContrasenas gestor = new GestorContrasenas();
        ServicioAutenticacion servicio = new ServicioAutenticacion(
                RepositorioUsuariosMemoria.conUsuariosPorDefecto(gestor),
                new RepositorioSesionesMemoria(),
                new RepositorioAuditoriaAccesoMemoria(),
                gestor,
                new GeneradorTokens(),
                new ProveedorSSOSimulado()
        );
        servidor = new ServidorHTTP(0, servicio);
        servidor.iniciar();
        baseUrl = "http://localhost:" + servidor.getPuerto();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        servidor.detener();
    }

    @Test
    void flujoCompletoLoginValidacionYLogout() throws Exception {
        HttpResponse<String> loginRes = post("/api/auth/login",
                "{\"username\":\"medico1\",\"password\":\"medico123\"}", null);
        assertEquals(200, loginRes.statusCode());
        Map<String, String> loginJson = JsonUtil.parsearObjetoPlano(loginRes.body());
        assertEquals("true", loginJson.get("exito"));
        assertEquals("MEDICO", loginJson.get("rol"));
        String token = loginJson.get("token");
        assertNotNull(token);

        HttpResponse<String> sessionRes = get("/api/auth/session", token);
        assertEquals(200, sessionRes.statusCode());
        Map<String, String> sessionJson = JsonUtil.parsearObjetoPlano(sessionRes.body());
        assertEquals("medico1", sessionJson.get("username"));

        HttpResponse<String> logoutRes = post("/api/auth/logout", "", token);
        assertEquals(200, logoutRes.statusCode());

        HttpResponse<String> afterLogout = get("/api/auth/session", token);
        assertEquals(401, afterLogout.statusCode());
    }

    @Test
    void loginSSOExitosoYCredencialesInvalidas() throws Exception {
        HttpResponse<String> ssoOk = post("/api/auth/sso", "{\"ssoToken\":\"sso-sescam-admin1\"}", null);
        assertEquals(200, ssoOk.statusCode());
        Map<String, String> ssoJson = JsonUtil.parsearObjetoPlano(ssoOk.body());
        assertEquals("ADMINISTRADOR", ssoJson.get("rol"));
        assertEquals("SSO_CORPORATIVO", ssoJson.get("origenAutenticacion"));

        HttpResponse<String> badLogin = post("/api/auth/login",
                "{\"username\":\"admin1\",\"password\":\"erronea\"}", null);
        assertEquals(401, badLogin.statusCode());
    }

    private HttpResponse<String> post(String ruta, String body, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + ruta))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String ruta, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + ruta))
                .GET();
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
