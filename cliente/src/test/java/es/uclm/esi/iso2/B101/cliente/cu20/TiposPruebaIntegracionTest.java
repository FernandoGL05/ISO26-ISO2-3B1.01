package es.uclm.esi.iso2.B101.cliente.cu20;

import static org.junit.jupiter.api.Assertions.*;

import es.uclm.esi.iso2.B101.servidor.api.AplicacionServidor;
import es.uclm.esi.iso2.B101.servidor.api.JsonUtil;
import es.uclm.esi.iso2.B101.servidor.api.ServidorHTTP;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TiposPruebaIntegracionTest {

    private ServidorHTTP servidor;
    private HttpClient clienteHttp;
    private String urlBase;

    @BeforeEach
    void preparar() throws Exception {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-28T10:00:00Z"), ZoneOffset.UTC);
        servidor = AplicacionServidor.crearServidor(0, reloj, new RepositorioCitasMemoria());
        servidor.iniciar();
        urlBase = "http://127.0.0.1:" + servidor.getPuerto();
        clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void cerrar() {
        if (clienteHttp != null) {
            clienteHttp.close();
        }
        if (servidor != null) {
            servidor.detener();
        }
    }

    @Test
    void completaElFlujoConLoginRealDeAdministrador() throws Exception {
        String token = iniciarSesion("admin1", "admin123");

        try (var cliente = new ClienteTiposPruebaHttp(urlBase, token)) {
            var creado = cliente.crearTipoPrueba(
                    "TAC Torácico", "TAC", "Tomógrafo 64 cortes", "Sala TAC-1",
                    25, "Ayuno de 4 horas", true, true,
                    "Evaluación renal previa", "PREFERENTE", true);
            assertEquals(201, creado.codigoHttp());

            String id = JsonUtil.parsearObjetoPlano(creado.cuerpoJson()).get("id");
            assertNotNull(id);

            var listado = cliente.listarTiposPrueba(null);
            assertEquals(200, listado.codigoHttp());
            assertTrue(listado.cuerpoJson().contains("TAC Torácico"));

            var actualizado = cliente.actualizarTipoPrueba(
                    id, "TAC Torácico Alta Resolución", "TAC", "Tomógrafo 128 cortes", "Sala TAC-2",
                    30, "Ayuno de 6 horas", true, true,
                    "Control creatinina", "URGENTE", false);
            assertEquals(200, actualizado.codigoHttp());
            assertTrue(actualizado.cuerpoJson().contains("TAC Torácico Alta Resolución"));

            var desactivado = cliente.cambiarEstado(id, false);
            assertEquals(200, desactivado.codigoHttp());
            assertTrue(desactivado.cuerpoJson().contains("\"activo\":false"));

            var activos = cliente.listarTiposPrueba(true);
            assertEquals(200, activos.codigoHttp());
            assertEquals("[]", activos.cuerpoJson());

            var logout = pedir("POST", "/api/auth/logout", null, token);
            assertEquals(200, logout.statusCode());
            assertEquals(401, cliente.listarTiposPrueba(null).codigoHttp());
        }
    }

    @Test
    void exigeSesionYRolAdministradorEnLaAplicacionReal() throws Exception {
        assertEquals(401, pedir("GET", "/api/tipos-prueba", null, null).statusCode());

        try (var clienteInvalido = new ClienteTiposPruebaHttp(urlBase, "token-invalido")) {
            assertEquals(401, clienteInvalido.listarTiposPrueba(null).codigoHttp());
        }

        String[][] noAdmins = {
            {"paciente1", "paciente123"},
            {"medico1", "medico123"},
            {"admision1", "admision123"}
        };
        for (String[] cred : noAdmins) {
            String token = iniciarSesion(cred[0], cred[1]);
            try (var cliente = new ClienteTiposPruebaHttp(urlBase, token)) {
                assertEquals(403, cliente.listarTiposPrueba(null).codigoHttp());
                assertEquals(403, cliente.crearTipoPrueba(
                        "Rx Simple", "Radiografía", "Rx Digital", "Sala 1",
                        10, "Ninguna", false, false, "Plomo", "ORDINARIA", false).codigoHttp());
            }
        }
    }

    private String iniciarSesion(String usuario, String contrasena) throws Exception {
        String cuerpo = JsonUtil.aJson(Map.of("username", usuario, "password", contrasena));
        var respuesta = pedir("POST", "/api/auth/login", cuerpo, null);
        assertEquals(200, respuesta.statusCode());
        return JsonUtil.parsearObjetoPlano(respuesta.body()).get("token");
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String cuerpo, String token) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder()
                .uri(URI.create(urlBase + ruta))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        if (cuerpo != null) {
            peticion.header("Content-Type", "application/json; charset=utf-8");
        }
        peticion.method(metodo, cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        return clienteHttp.send(peticion.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
