package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioHistorialPruebas;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistorialPruebasHttpHandlerTest {
    private HttpServer servidor;
    private HttpClient cliente;
    private ServicioHistorialPruebas servicio;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        servicio = new ServicioHistorialPruebas(new RepositorioHistorialPruebasMemoria());
        AutorizadorHistorialPruebas autorizador = cabecera -> {
            if ("Bearer token-paciente".equals(cabecera)) {
                return Optional.of(new AutorizadorHistorialPruebas.IdentidadPaciente(true, "paciente-1"));
            }
            if ("Bearer token-otro".equals(cabecera)) {
                return Optional.of(new AutorizadorHistorialPruebas.IdentidadPaciente(true, "paciente-2"));
            }
            if ("Bearer token-sin-rol".equals(cabecera)) {
                return Optional.of(new AutorizadorHistorialPruebas.IdentidadPaciente(false, "usuario-1"));
            }
            return Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        HistorialPruebasHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        cliente = HttpClient.newHttpClient();
    }

    @AfterEach
    void detenerServidorTemporal() {
        servidor.stop(0);
    }

    @Test
    void devuelveSoloLasPruebasDelPacienteConJsonYCaracteresEscapados() throws Exception {
        servicio.registrarPruebaRealizada(
                "prueba-1",
                "paciente-1",
                "Tomografía \"torácica\" \\ especial\n",
                LocalDateTime.of(2026, 3, 15, 10, 30),
                "solicitud-1");
        servicio.registrarPruebaRealizada(
                "prueba-ajena",
                "paciente-2",
                "Radiografía",
                LocalDateTime.of(2026, 3, 16, 10, 30),
                null);

        HttpResponse<String> respuesta =
                enviar("GET", "/api/historial-pruebas", "Bearer token-paciente");

        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(respuesta.body().startsWith("["));
        assertTrue(respuesta.body().contains("\"fechaHoraRealizacion\":\"2026-03-15T10:30\""));
        assertTrue(respuesta.body().contains("Tomografía \\\"torácica\\\" \\\\ especial\\n"));
        assertFalse(respuesta.body().contains("prueba-ajena"));
        assertFalse(respuesta.body().contains("paciente-1"));
    }

    @Test
    void devuelveArrayVacioCuandoNoHayPruebasRealizadas() throws Exception {
        HttpResponse<String> respuesta =
                enviar("GET", "/api/historial-pruebas", "Bearer token-paciente");

        assertEquals(200, respuesta.statusCode());
        assertEquals("[]", respuesta.body());
    }

    @Test
    void rechazaPeticionesSinAutenticacionOTokenValido() throws Exception {
        HttpResponse<String> sinToken = enviar("GET", "/api/historial-pruebas", null);
        HttpResponse<String> tokenInvalido = enviar("GET", "/api/historial-pruebas", "Bearer invalido");

        assertEquals(401, sinToken.statusCode());
        assertEquals(401, tokenInvalido.statusCode());
        assertTrue(sinToken.body().contains("\"codigoError\":\"NO_AUTENTICADO\""));
    }

    @Test
    void rechazaUsuariosSinRolPaciente() throws Exception {
        HttpResponse<String> respuesta =
                enviar("GET", "/api/historial-pruebas", "Bearer token-sin-rol");

        assertEquals(403, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"codigoError\":\"PROHIBIDO\""));
    }

    @Test
    void rechazaMetodosIncorrectosYParametrosDePacienteEnLaUrl() throws Exception {
        HttpResponse<String> metodoIncorrecto =
                enviar("POST", "/api/historial-pruebas", "Bearer token-paciente");
        HttpResponse<String> selectorPaciente =
                enviar("GET", "/api/historial-pruebas?pacienteId=paciente-2", "Bearer token-paciente");

        assertEquals(405, metodoIncorrecto.statusCode());
        assertEquals("GET", metodoIncorrecto.headers().firstValue("Allow").orElse(""));
        assertEquals(400, selectorPaciente.statusCode());
        assertFalse(selectorPaciente.body().contains("paciente-2"));
    }

    @Test
    void rechazaRutasQueSoloCoincidenPorPrefijo() throws Exception {
        HttpResponse<String> respuesta =
                enviar("GET", "/api/historial-pruebas-extra", "Bearer token-paciente");

        assertEquals(404, respuesta.statusCode());
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String autorizacion) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create(urlBase() + ruta))
                .header("Accept", "application/json")
                .method(
                        metodo,
                        metodo.equals("GET")
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString("", StandardCharsets.UTF_8));
        if (autorizacion != null) {
            solicitud.header("Authorization", autorizacion);
        }
        return cliente.send(solicitud.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String urlBase() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }
}
