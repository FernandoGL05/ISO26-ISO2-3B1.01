package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioIncidencias;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncidenciasHttpHandlerTest {
    private HttpServer servidor;
    private HttpClient cliente;
    private ServicioIncidencias servicio;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        servicio = new ServicioIncidencias(
                new RepositorioIncidenciasMemoria(),
                Clock.fixed(Instant.parse("2026-11-21T11:00:00Z"), ZoneOffset.UTC));
        AutorizadorIncidencias autorizador = cabecera -> {
            if ("Bearer token-paciente".equals(cabecera)) {
                return Optional.of(new AutorizadorIncidencias.IdentidadPaciente(true, "paciente-1"));
            }
            if ("Bearer token-otro".equals(cabecera)) {
                return Optional.of(new AutorizadorIncidencias.IdentidadPaciente(true, "paciente-2"));
            }
            if ("Bearer token-sin-rol".equals(cabecera)) {
                return Optional.of(new AutorizadorIncidencias.IdentidadPaciente(false, "usuario-1"));
            }
            return Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        IncidenciasHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        cliente = HttpClient.newHttpClient();
    }

    @AfterEach
    void detenerServidorTemporal() {
        servidor.stop(0);
    }

    @Test
    void creaIncidenciaConHttpYEscapaCaracteresEspeciales() throws Exception {
        String cuerpo =
                "{\"asunto\":\"Problema \\\"importante\\\"\",\"descripcion\":\"Primera línea\\nSegunda \\\\ línea\",\"citaId\":\"cita-123\"}";

        HttpResponse<String> respuesta =
                enviar("POST", "/api/incidencias", "Bearer token-paciente", cuerpo, "application/json");

        assertEquals(201, respuesta.statusCode());
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(respuesta.body().contains("\"estado\":\"RECIBIDA\""));
        assertTrue(respuesta.body().contains("\"asunto\":\"Problema \\\"importante\\\"\""));
        assertTrue(respuesta.body().contains("\"descripcion\":\"Primera línea\\nSegunda \\\\ línea\""));
        assertTrue(respuesta.body().contains("\"citaId\":\"cita-123\""));
        assertEquals(1, servicio.consultarIncidencias("paciente-1").size());
    }

    @Test
    void permiteCrearIncidenciaSinCitaRelacionada() throws Exception {
        HttpResponse<String> respuesta = enviar(
                "POST",
                "/api/incidencias",
                "Bearer token-paciente",
                "{\"asunto\":\"Otro asunto\",\"descripcion\":\"Descripción válida\"}",
                "application/json; charset=utf-8");

        assertEquals(201, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"citaId\":null"));
    }

    @Test
    void consultaLaListaPropiaYElDetalle() throws Exception {
        String creada = servicio.comunicarIncidencia("paciente-1", "Propia", "Descripción propia", null).getId();
        servicio.comunicarIncidencia("paciente-2", "Ajena", "No debe aparecer.", null);

        HttpResponse<String> lista = enviar("GET", "/api/incidencias", "Bearer token-paciente", null, null);
        HttpResponse<String> detalle =
                enviar("GET", "/api/incidencias/" + creada, "Bearer token-paciente", null, null);

        assertEquals(200, lista.statusCode());
        assertTrue(lista.body().startsWith("["));
        assertTrue(lista.body().contains("Propia"));
        assertFalse(lista.body().contains("Ajena"));
        assertEquals(200, detalle.statusCode());
        assertTrue(detalle.body().contains("\"id\":\"" + creada + "\""));
    }

    @Test
    void impideConsultarElDetalleDeUnaIncidenciaAjena() throws Exception {
        String idAjeno = servicio.comunicarIncidencia("paciente-2", "Ajena", "Descripción", null).getId();

        HttpResponse<String> respuesta =
                enviar("GET", "/api/incidencias/" + idAjeno, "Bearer token-paciente", null, null);

        assertEquals(404, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"codigoError\":\"NO_ENCONTRADO\""));
    }

    @Test
    void rechazaPeticionesSinTokenOTokenInvalido() throws Exception {
        HttpResponse<String> sinToken = enviar("GET", "/api/incidencias", null, null, null);
        HttpResponse<String> invalido = enviar("GET", "/api/incidencias", "Bearer invalido", null, null);

        assertEquals(401, sinToken.statusCode());
        assertEquals(401, invalido.statusCode());
        assertTrue(sinToken.body().contains("\"exito\":false"));
    }

    @Test
    void rechazaUsuariosSinRolPaciente() throws Exception {
        HttpResponse<String> respuesta =
                enviar("GET", "/api/incidencias", "Bearer token-sin-rol", null, null);

        assertEquals(403, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"codigoError\":\"PROHIBIDO\""));
    }

    @Test
    void rechazaJsonInvalidoDatosInvalidosYExcesoDeTamano() throws Exception {
        HttpResponse<String> jsonInvalido = enviar(
                "POST", "/api/incidencias", "Bearer token-paciente", "{\"asunto\":\"sin cerrar\"", "application/json");
        HttpResponse<String> pacienteElegidoPorCliente = enviar(
                "POST",
                "/api/incidencias",
                "Bearer token-paciente",
                "{\"pacienteId\":\"paciente-2\",\"asunto\":\"Asunto\",\"descripcion\":\"Texto\"}",
                "application/json");
        HttpResponse<String> cuerpoExcesivo = enviar(
                "POST",
                "/api/incidencias",
                "Bearer token-paciente",
                "{\"asunto\":\"a\",\"descripcion\":\"" + "x".repeat(17_000) + "\"}",
                "application/json");

        assertEquals(400, jsonInvalido.statusCode());
        assertEquals(400, pacienteElegidoPorCliente.statusCode());
        assertEquals(400, cuerpoExcesivo.statusCode());
        assertTrue(servicio.consultarIncidencias("paciente-1").isEmpty());
    }

    @Test
    void rechazaMetodosIncorrectosConsultasConPacienteYRutasIncorrectas() throws Exception {
        HttpResponse<String> metodo = enviar("DELETE", "/api/incidencias", "Bearer token-paciente", null, null);
        HttpResponse<String> parametro = enviar(
                "GET", "/api/incidencias?pacienteId=paciente-2", "Bearer token-paciente", null, null);
        HttpResponse<String> ruta = enviar("GET", "/api/incidencias-extra", "Bearer token-paciente", null, null);

        assertEquals(405, metodo.statusCode());
        assertEquals("GET, POST", metodo.headers().firstValue("Allow").orElse(""));
        assertEquals(400, parametro.statusCode());
        assertEquals(404, ruta.statusCode());
    }

    private HttpResponse<String> enviar(
            String metodo, String ruta, String autorizacion, String cuerpo, String tipoContenido) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create(urlBase() + ruta))
                .header("Accept", "application/json");
        if (autorizacion != null) {
            solicitud.header("Authorization", autorizacion);
        }
        if (tipoContenido != null) {
            solicitud.header("Content-Type", tipoContenido);
        }
        HttpRequest.BodyPublisher body = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8);
        return cliente.send(
                solicitud.method(metodo, body).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String urlBase() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }
}
