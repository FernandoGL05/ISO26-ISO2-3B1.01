package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNecesidadesCitaSeguimientoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesSeguimientoMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesSeguimiento;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrescripcionesSeguimientoHttpHandlerTest {
    private static final String RUTA = "/api/prescripciones-seguimiento";

    private HttpServer servidor;
    private HttpClient cliente;
    private AtomicInteger siguienteId;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        siguienteId = new AtomicInteger();
        ServicioPrescripcionesSeguimiento servicio = new ServicioPrescripcionesSeguimiento(
                new RepositorioPrescripcionesSeguimientoMemoria(),
                new RepositorioNecesidadesCitaSeguimientoMemoria(),
                Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC),
                (medico, paciente) -> !"denegado".equals(paciente),
                2,
                12,
                () -> "id-" + siguienteId.incrementAndGet());
        AutorizadorPrescripcionesSeguimiento autorizador = cabecera -> switch (cabecera) {
            case "Bearer medico-1" -> Optional.of(
                    new AutorizadorPrescripcionesSeguimiento.IdentidadMedico(true, "medico-1"));
            case "Bearer medico-2" -> Optional.of(
                    new AutorizadorPrescripcionesSeguimiento.IdentidadMedico(true, "medico-2"));
            case "Bearer sin-rol" -> Optional.of(
                    new AutorizadorPrescripcionesSeguimiento.IdentidadMedico(false, "usuario-1"));
            default -> Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        PrescripcionesSeguimientoHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        cliente = HttpClient.newHttpClient();
    }

    @AfterEach
    void detenerServidorTemporal() {
        servidor.stop(0);
    }

    @Test
    void creaPrescripcionYConsultaPrescripcionesDetalleYNecesidades() throws Exception {
        HttpResponse<String> creada = enviar(
                "POST",
                RUTA,
                "Bearer medico-1",
                "application/json",
                cuerpo("paciente-1", "TAC \"control\"", "Seguimiento de lesión", 1, "2024-01-31", "URGENTE"));

        assertEquals(201, creada.statusCode());
        assertTrue(creada.body().contains("\"fechaCreacion\":\"2024-01-01\""));
        assertTrue(creada.body().contains("\"estado\":\"ACTIVA\""));
        assertTrue(creada.body().contains("\"prioridad\":\"URGENTE\""));

        HttpResponse<String> lista = enviar("GET", RUTA, "Bearer medico-1", null, null);
        HttpResponse<String> detalle = enviar("GET", RUTA + "/id-1", "Bearer medico-1", null, null);
        HttpResponse<String> necesidades =
                enviar("GET", RUTA + "/id-1/necesidades-cita", "Bearer medico-1", null, null);
        assertEquals(200, lista.statusCode());
        assertTrue(lista.body().contains("\"id\":\"id-1\""));
        assertEquals(200, detalle.statusCode());
        assertTrue(detalle.body().contains("Seguimiento de lesión"));
        assertEquals(200, necesidades.statusCode());
        assertTrue(necesidades.body().contains("\"fechaPrevista\":\"2024-01-31\""));
        assertTrue(necesidades.body().contains("\"estado\":\"PENDIENTE_PROGRAMACION\""));
        assertTrue(necesidades.body().contains("\"citaId\":null"));
    }

    @Test
    void aceptaTodasLasPrioridadesYDevuelveSoloLasPrescripcionesPropias() throws Exception {
        for (String prioridad : new String[] {"URGENTE", "PREFERENTE", "ORDINARIA"}) {
            HttpResponse<String> respuesta = enviar(
                    "POST",
                    RUTA,
                    "Bearer medico-1",
                    "application/json",
                    cuerpo("paciente-1", "RM", "Control", 3, "2024-02-01", prioridad));
            assertEquals(201, respuesta.statusCode());
            assertTrue(respuesta.body().contains("\"prioridad\":\"" + prioridad + "\""));
        }
        HttpResponse<String> listaAjena = enviar("GET", RUTA, "Bearer medico-2", null, null);
        assertEquals("[]", listaAjena.body());
        HttpResponse<String> detalleAjeno = enviar("GET", RUTA + "/id-1", "Bearer medico-2", null, null);
        assertEquals(404, detalleAjeno.statusCode());
        assertFalse(detalleAjeno.body().contains("paciente-1"));
        assertEquals(
                404,
                enviar("GET", RUTA + "/id-1/necesidades-cita", "Bearer medico-2", null, null).statusCode());
    }

    @Test
    void exigeAutenticacionRolMedicoYAutorizacionSobreElPaciente() throws Exception {
        assertEquals(401, enviar("GET", RUTA, null, null, null).statusCode());
        assertEquals(401, enviar("GET", RUTA, "Bearer invalido", null, null).statusCode());
        HttpResponse<String> sinRol = enviar("GET", RUTA, "Bearer sin-rol", null, null);
        assertEquals(403, sinRol.statusCode());
        assertTrue(sinRol.body().contains("MEDICO"));
        HttpResponse<String> denegada = enviar(
                "POST",
                RUTA,
                "Bearer medico-1",
                "application/json",
                cuerpo("denegado", "RM", "Control", 1, "2024-02-01", "PREFERENTE"));
        assertEquals(403, denegada.statusCode());
        assertEquals("[]", enviar("GET", RUTA, "Bearer medico-1", null, null).body());
    }

    @Test
    void validaJsonCamposPeriodicidadFechaPrioridadYLimiteDeCuerpo() throws Exception {
        assertEquals(400, enviar("POST", RUTA, "Bearer medico-1", "application/json", "{").statusCode());
        assertEquals(
                400,
                enviar(
                        "POST",
                        RUTA,
                        "Bearer medico-1",
                        "application/json",
                        cuerpo("p", "TAC", "Control", 0, "2024-02-01", "URGENTE")).statusCode());
        assertEquals(
                400,
                enviar(
                        "POST",
                        RUTA,
                        "Bearer medico-1",
                        "application/json",
                        cuerpo("p", "TAC", "Control", 1, "2024-02-30", "URGENTE")).statusCode());
        assertEquals(
                400,
                enviar(
                        "POST",
                        RUTA,
                        "Bearer medico-1",
                        "application/json",
                        cuerpo("p", "TAC", "Control", 1, "2024-02-01", "urgente")).statusCode());
        assertEquals(400, enviar("POST", RUTA, "Bearer medico-1", null, "{}").statusCode());
        assertEquals(
                400,
                enviar("POST", RUTA, "Bearer medico-1", "application/json", " ".repeat(16 * 1024 + 1))
                        .statusCode());
    }

    @Test
    void respondeConMetodosRutasConsultasDuplicadasYCodigosCorrectos() throws Exception {
        HttpResponse<String> metodo = enviar("PUT", RUTA, "Bearer medico-1", null, null);
        assertEquals(405, metodo.statusCode());
        assertEquals("GET, POST", metodo.headers().firstValue("Allow").orElse(""));
        assertEquals(405, enviar("POST", RUTA + "/id-1", "Bearer medico-1", null, null).statusCode());
        assertEquals(404, enviar("GET", RUTA + "/id-no-existe", "Bearer medico-1", null, null).statusCode());
        assertEquals(404, enviar("GET", RUTA + "-extra", "Bearer medico-1", null, null).statusCode());
        assertEquals(400, enviar("GET", RUTA + "?medicoId=otro", "Bearer medico-1", null, null).statusCode());

        String datos = cuerpo("p", "TAC", "Control", 2, "2024-02-01", "ORDINARIA");
        assertEquals(201, enviar("POST", RUTA, "Bearer medico-1", "application/json", datos).statusCode());
        siguienteId.set(0);
        assertEquals(409, enviar("POST", RUTA, "Bearer medico-1", "application/json", datos).statusCode());
    }

    private HttpResponse<String> enviar(
            String metodo, String ruta, String autorizacion, String tipoContenido, String cuerpo) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + ruta))
                .header("Accept", "application/json")
                .method(
                        metodo,
                        cuerpo == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        if (autorizacion != null) {
            solicitud.header("Authorization", autorizacion);
        }
        if (tipoContenido != null) {
            solicitud.header("Content-Type", tipoContenido);
        }
        return cliente.send(solicitud.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String cuerpo(
            String pacienteId,
            String tipo,
            String motivo,
            int periodicidad,
            String primeraRevision,
            String prioridad) {
        return "{\"pacienteId\":" + cadena(pacienteId)
                + ",\"tipoPrueba\":" + cadena(tipo)
                + ",\"motivoClinico\":" + cadena(motivo)
                + ",\"periodicidadMeses\":" + periodicidad
                + ",\"fechaPrimeraRevision\":" + cadena(primeraRevision)
                + ",\"prioridad\":" + cadena(prioridad) + "}";
    }

    private static String cadena(String valor) {
        return "\"" + valor.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
