package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSeguimientosMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioSeguimientos;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeguimientosHttpHandlerTest {
    private HttpServer servidor;
    private HttpClient cliente;
    private ServicioSeguimientos servicio;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        servicio = new ServicioSeguimientos(new RepositorioSeguimientosMemoria());
        servicio.registrarSeguimiento(new SeguimientoPaciente(
                "s1",
                "paciente-1",
                "Resonancia \"anual\"",
                "Primera línea\nSegunda línea",
                12,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                "Traer \"documentación\"\\adicional"));
        servicio.registrarSeguimiento(
                new SeguimientoPaciente("s2", "paciente-1", "Control", "Sin fecha", null, null, null, null));
        servicio.registrarSeguimiento(
                new SeguimientoPaciente("ajeno", "paciente-2", "No visible", "Otro", 3, null, null, null));
        AutorizadorSeguimientos autorizador = cabecera -> {
            if ("Bearer token-paciente".equals(cabecera)) {
                return Optional.of(new AutorizadorSeguimientos.IdentidadPaciente(true, "paciente-1"));
            }
            if ("Bearer token-otro".equals(cabecera)) {
                return Optional.of(new AutorizadorSeguimientos.IdentidadPaciente(true, "paciente-2"));
            }
            if ("Bearer token-sin-rol".equals(cabecera)) {
                return Optional.of(new AutorizadorSeguimientos.IdentidadPaciente(false, "usuario-1"));
            }
            return Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        SeguimientosHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        cliente = HttpClient.newHttpClient();
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void consultaListadoPropioConJsonEscapadoYFechasAusentesComoNull() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/api/seguimientos", "Bearer token-paciente");

        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(respuesta.body().contains("\"tipoPrueba\":\"Resonancia \\\"anual\\\"\""));
        assertTrue(respuesta.body().contains("\"descripcion\":\"Primera línea\\nSegunda línea\""));
        assertTrue(respuesta.body().contains("\"observacionesPreparacion\":\"Traer \\\"documentación\\\"\\\\adicional\""));
        assertTrue(respuesta.body().contains("\"fechaProximaRevision\":null"));
        assertFalse(respuesta.body().contains("No visible"));
        assertFalse(respuesta.body().contains("pacienteId"));
    }

    @Test
    void consultaDetallePropioYDevuelve404ParaDetalleAjenoOInexistente() throws Exception {
        HttpResponse<String> propio = enviar("GET", "/api/seguimientos/s1", "Bearer token-paciente");
        HttpResponse<String> ajeno = enviar("GET", "/api/seguimientos/ajeno", "Bearer token-paciente");
        HttpResponse<String> ausente = enviar("GET", "/api/seguimientos/no-existe", "Bearer token-paciente");

        assertEquals(200, propio.statusCode());
        assertTrue(propio.body().contains("\"id\":\"s1\""));
        assertEquals(404, ajeno.statusCode());
        assertEquals(404, ausente.statusCode());
    }

    @Test
    void rechazaAutenticacionAusenteInvalidaYRolNoPaciente() throws Exception {
        HttpResponse<String> ausente = enviar("GET", "/api/seguimientos", null);
        HttpResponse<String> invalido = enviar("GET", "/api/seguimientos", "Bearer invalido");
        HttpResponse<String> sinRol = enviar("GET", "/api/seguimientos", "Bearer token-sin-rol");

        assertEquals(401, ausente.statusCode());
        assertEquals(401, invalido.statusCode());
        assertEquals(403, sinRol.statusCode());
    }

    @Test
    void rechazaMetodoIncorrectoConsultaNoAdmitidaYRutaDesconocida() throws Exception {
        HttpResponse<String> metodo = enviar("DELETE", "/api/seguimientos", "Bearer token-paciente");
        HttpResponse<String> query = enviar("GET", "/api/seguimientos?pacienteId=paciente-2", "Bearer token-paciente");
        HttpResponse<String> ruta = enviar("GET", "/api/seguimientos/s1/extra", "Bearer token-paciente");

        assertEquals(405, metodo.statusCode());
        assertEquals("GET", metodo.headers().firstValue("Allow").orElse(""));
        assertEquals(400, query.statusCode());
        assertEquals(404, ruta.statusCode());
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String autorizacion) throws Exception {
        HttpRequest.Builder solicitud =
                HttpRequest.newBuilder(URI.create(urlBase() + ruta)).header("Accept", "application/json");
        if (autorizacion != null) {
            solicitud.header("Authorization", autorizacion);
        }
        return cliente.send(
                solicitud.method(metodo, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String urlBase() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }
}
