package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.EncuestaSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioEncuestasSatisfaccionMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioRespuestasEncuestasMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioEncuestasSatisfaccion;
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

class EncuestasSatisfaccionHttpHandlerTest {
    private HttpServer servidor;
    private HttpClient cliente;
    private ServicioEncuestasSatisfaccion servicio;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        RepositorioEncuestasSatisfaccionMemoria repositorio = new RepositorioEncuestasSatisfaccionMemoria();
        repositorio.guardar(new EncuestaSatisfaccion("e1", "Atención \"recibida\"", "Valore el servicio", true));
        repositorio.guardar(new EncuestaSatisfaccion("e2", "Cerrada", "No disponible", false));
        servicio = new ServicioEncuestasSatisfaccion(
                repositorio,
                new RepositorioRespuestasEncuestasMemoria(),
                Clock.fixed(Instant.parse("2026-11-22T09:15:00Z"), ZoneOffset.UTC));
        AutorizadorEncuestas autorizador = cabecera -> {
            if ("Bearer token-paciente".equals(cabecera)) {
                return Optional.of(new AutorizadorEncuestas.IdentidadPaciente(true, "paciente-1"));
            }
            if ("Bearer token-otro".equals(cabecera)) {
                return Optional.of(new AutorizadorEncuestas.IdentidadPaciente(true, "paciente-2"));
            }
            if ("Bearer token-sin-rol".equals(cabecera)) {
                return Optional.of(new AutorizadorEncuestas.IdentidadPaciente(false, "usuario-1"));
            }
            return Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        EncuestasSatisfaccionHttpHandler.registrarEn(servidor, servicio, autorizador);
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
    void consultaDisponiblesYGeneraJsonEscapadoValido() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/api/encuestas", "Bearer token-paciente", null, null);

        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(respuesta.body().contains("\"id\":\"e1\""));
        assertTrue(respuesta.body().contains("\"titulo\":\"Atención \\\"recibida\\\"\""));
        assertFalse(respuesta.body().contains("\"id\":\"e2\""));
    }

    @Test
    void respondeUnaVezYDevuelveLaRespuestaSinIdentidadDelPaciente() throws Exception {
        HttpResponse<String> primera = enviar(
                "POST",
                "/api/encuestas/e1/respuestas",
                "Bearer token-paciente",
                "{\"puntuacion\":5,\"comentario\":\"Buena atención \\\"gracias\\\"\\n\"}",
                "application/json");
        HttpResponse<String> duplicada = enviar(
                "POST",
                "/api/encuestas/e1/respuestas",
                "Bearer token-paciente",
                "{\"puntuacion\":4,\"comentario\":null}",
                "application/json");

        assertEquals(201, primera.statusCode());
        assertTrue(primera.body().contains("\"puntuacion\":5"));
        assertTrue(primera.body().contains("\"comentario\":\"Buena atención \\\"gracias\\\"\\n\""));
        assertTrue(primera.body().contains("\"fechaRespuesta\":\"2026-11-22T09:15\""));
        assertFalse(primera.body().contains("pacienteId"));
        assertEquals(409, duplicada.statusCode());
    }

    @Test
    void misRespuestasSeObtienenDeLaIdentidadAutenticada() throws Exception {
        enviar(
                "POST",
                "/api/encuestas/e1/respuestas",
                "Bearer token-paciente",
                "{\"puntuacion\":5}",
                "application/json");
        enviar(
                "POST",
                "/api/encuestas/e1/respuestas",
                "Bearer token-otro",
                "{\"puntuacion\":2}",
                "application/json");

        HttpResponse<String> propia =
                enviar("GET", "/api/encuestas/mis-respuestas", "Bearer token-paciente", null, null);
        HttpResponse<String> otra =
                enviar("GET", "/api/encuestas/mis-respuestas", "Bearer token-otro", null, null);

        assertEquals(200, propia.statusCode());
        assertEquals(1, contar(propia.body(), "\"encuestaId\":\"e1\""));
        assertTrue(propia.body().contains("\"puntuacion\":5"));
        assertFalse(propia.body().contains("\"puntuacion\":2"));
        assertTrue(otra.body().contains("\"puntuacion\":2"));
    }

    @Test
    void devuelve401SinAutenticacionOVectorDeTokenInvalidoY403PorRol() throws Exception {
        HttpResponse<String> sinToken = enviar("GET", "/api/encuestas", null, null, null);
        HttpResponse<String> invalido = enviar("GET", "/api/encuestas", "Bearer invalido", null, null);
        HttpResponse<String> sinRol = enviar("GET", "/api/encuestas", "Bearer token-sin-rol", null, null);

        assertEquals(401, sinToken.statusCode());
        assertEquals(401, invalido.statusCode());
        assertEquals(403, sinRol.statusCode());
    }

    @Test
    void rechazaPuntuacionComentarioNoValidoCamposDeIdentidadYCuerpoMalformado() throws Exception {
        HttpResponse<String> puntuacion = enviar(
                "POST",
                "/api/encuestas/e1/respuestas",
                "Bearer token-paciente",
                "{\"puntuacion\":0}",
                "application/json");
        HttpResponse<String> comentario = enviar(
                "POST",
                "/api/encuestas/e1/respuestas",
                "Bearer token-paciente",
                "{\"puntuacion\":3,\"comentario\":\"" + "x".repeat(2001) + "\"}",
                "application/json");
        HttpResponse<String> identidadCliente = enviar(
                "POST",
                "/api/encuestas/e1/respuestas",
                "Bearer token-paciente",
                "{\"puntuacion\":4,\"pacienteId\":\"paciente-2\"}",
                "application/json");
        HttpResponse<String> jsonMalformado = enviar(
                "POST", "/api/encuestas/e1/respuestas", "Bearer token-paciente", "{\"puntuacion\":", "application/json");

        assertEquals(400, puntuacion.statusCode());
        assertEquals(400, comentario.statusCode());
        assertEquals(400, identidadCliente.statusCode());
        assertEquals(400, jsonMalformado.statusCode());
        assertTrue(servicio.consultarMisRespuestas("paciente-1").isEmpty());
    }

    @Test
    void rechazaEncuestaNoDisponibleEInexistente() throws Exception {
        HttpResponse<String> cerrada = enviar(
                "POST",
                "/api/encuestas/e2/respuestas",
                "Bearer token-paciente",
                "{\"puntuacion\":4}",
                "application/json");
        HttpResponse<String> inexistente = enviar(
                "POST",
                "/api/encuestas/no-existe/respuestas",
                "Bearer token-paciente",
                "{\"puntuacion\":4}",
                "application/json");

        assertEquals(409, cerrada.statusCode());
        assertEquals(404, inexistente.statusCode());
    }

    @Test
    void devuelve405404Y400ParaMetodoRutaYConsultaNoAdmitidos() throws Exception {
        HttpResponse<String> metodo = enviar("DELETE", "/api/encuestas", "Bearer token-paciente", null, null);
        HttpResponse<String> ruta = enviar("GET", "/api/encuestas-extra", "Bearer token-paciente", null, null);
        HttpResponse<String> query =
                enviar("GET", "/api/encuestas?pacienteId=otro", "Bearer token-paciente", null, null);

        assertEquals(405, metodo.statusCode());
        assertEquals("GET", metodo.headers().firstValue("Allow").orElse(""));
        assertEquals(404, ruta.statusCode());
        assertEquals(400, query.statusCode());
    }

    private HttpResponse<String> enviar(
            String metodo, String ruta, String autorizacion, String cuerpo, String tipoContenido) throws Exception {
        HttpRequest.Builder solicitud =
                HttpRequest.newBuilder(URI.create(urlBase() + ruta)).header("Accept", "application/json");
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

    private static int contar(String texto, String buscado) {
        int cantidad = 0;
        int posicion = 0;
        while ((posicion = texto.indexOf(buscado, posicion)) >= 0) {
            cantidad++;
            posicion += buscado.length();
        }
        return cantidad;
    }
}
