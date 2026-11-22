package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesDiagnosticasMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesDiagnosticas;
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

class PrescripcionesPruebasHttpHandlerTest {
    private static final String RUTA = "/api/prescripciones-pruebas";

    private HttpServer servidor;
    private HttpClient cliente;
    private AtomicInteger siguienteId;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        siguienteId = new AtomicInteger();
        ServicioPrescripcionesDiagnosticas servicio = new ServicioPrescripcionesDiagnosticas(
                new RepositorioPrescripcionesDiagnosticasMemoria(),
                Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC),
                (medico, paciente) -> !"denegado".equals(paciente),
                () -> "solicitud-" + siguienteId.getAndIncrement());
        AutorizadorPrescripcionesDiagnosticas autorizador = cabecera -> switch (cabecera) {
            case "Bearer medico-1" -> Optional.of(
                    new AutorizadorPrescripcionesDiagnosticas.IdentidadMedico(true, "medico-1"));
            case "Bearer medico-2" -> Optional.of(
                    new AutorizadorPrescripcionesDiagnosticas.IdentidadMedico(true, "medico-2"));
            case "Bearer sin-rol" -> Optional.of(
                    new AutorizadorPrescripcionesDiagnosticas.IdentidadMedico(false, "usuario-1"));
            default -> Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        PrescripcionesPruebasHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        cliente = HttpClient.newHttpClient();
    }

    @AfterEach
    void detenerServidorTemporal() {
        servidor.stop(0);
    }

    @Test
    void creaConPrioridadFechaEstadoYJsonEscapadoYPermiteConsultarla() throws Exception {
        String body = cuerpo("paciente-1", "TAC \"torácico\" \\ especial\n", "Dolor intenso", "Descartar lesión", "URGENTE");

        HttpResponse<String> creada = enviar("POST", RUTA, "Bearer medico-1", "application/json", body);

        assertEquals(201, creada.statusCode());
        assertTrue(creada.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(creada.body().contains("\"tipoPrueba\":\"TAC \\\"torácico\\\" \\\\ especial\\n\""));
        assertTrue(creada.body().contains("\"prioridad\":\"URGENTE\""));
        assertTrue(creada.body().contains("\"fecha\":\"2026-11-22T10:00\""));
        assertTrue(creada.body().contains("\"estado\":\"PENDIENTE\""));

        HttpResponse<String> lista = enviar("GET", RUTA, "Bearer medico-1", null, null);
        HttpResponse<String> detalle = enviar("GET", RUTA + "/solicitud-0", "Bearer medico-1", null, null);
        assertEquals(200, lista.statusCode());
        assertTrue(lista.body().contains("\"id\":\"solicitud-0\""));
        assertEquals(200, detalle.statusCode());
        assertTrue(detalle.body().contains("\"pacienteId\":\"paciente-1\""));
    }

    @Test
    void soportaTodasLasPrioridadesDefinidas() throws Exception {
        for (String prioridad : new String[] {"URGENTE", "PREFERENTE", "ORDINARIA"}) {
            HttpResponse<String> respuesta =
                    enviar("POST", RUTA, "Bearer medico-1", "application/json",
                            cuerpo("p-1", "RM", "Indicación", "Justificación", prioridad));
            assertEquals(201, respuesta.statusCode());
            assertTrue(respuesta.body().contains("\"prioridad\":\"" + prioridad + "\""));
        }
    }

    @Test
    void aislaListasYDetallesPorMedicoSinConfirmarSolicitudesAjenas() throws Exception {
        enviar(
                "POST",
                RUTA,
                "Bearer medico-1",
                "application/json",
                cuerpo("paciente-secreto", "RM", "Indicación privada", "Justificación privada", "PREFERENTE"));

        HttpResponse<String> listaAjena = enviar("GET", RUTA, "Bearer medico-2", null, null);
        HttpResponse<String> detalleAjeno = enviar("GET", RUTA + "/solicitud-0", "Bearer medico-2", null, null);

        assertEquals("[]", listaAjena.body());
        assertEquals(404, detalleAjeno.statusCode());
        assertFalse(detalleAjeno.body().contains("paciente-secreto"));
        assertFalse(detalleAjeno.body().contains("Justificación privada"));
    }

    @Test
    void exigeBearerRolMedicoYPermisoSobreElPaciente() throws Exception {
        assertEquals(401, enviar("GET", RUTA, null, null, null).statusCode());
        assertEquals(401, enviar("GET", RUTA, "Bearer token-invalido", null, null).statusCode());
        HttpResponse<String> sinRol = enviar("GET", RUTA, "Bearer sin-rol", null, null);
        assertEquals(403, sinRol.statusCode());
        assertTrue(sinRol.body().contains("MEDICO"));
        HttpResponse<String> sinPermiso = enviar(
                "POST",
                RUTA,
                "Bearer medico-1",
                "application/json",
                cuerpo("denegado", "RM", "Indicación", "Justificación", "URGENTE"));
        assertEquals(403, sinPermiso.statusCode());
        assertEquals("[]", enviar("GET", RUTA, "Bearer medico-1", null, null).body());
    }

    @Test
    void rechazaJsonInvalidoCamposFaltantesPrioridadInvalidaYCuerposGrandes() throws Exception {
        assertEquals(400, enviar("POST", RUTA, "Bearer medico-1", "application/json", "{").statusCode());
        assertEquals(400, enviar(
                "POST", RUTA, "Bearer medico-1", "application/json",
                cuerpo("p", "RM", "I", "J", "URGENTÍSIMO")).statusCode());
        assertEquals(400, enviar(
                "POST", RUTA, "Bearer medico-1", "application/json", "{\"pacienteId\":\"p\"}").statusCode());
        assertEquals(400, enviar("POST", RUTA, "Bearer medico-1", null, "{}").statusCode());
        assertEquals(400, enviar(
                "POST", RUTA, "Bearer medico-1", "application/json", " ".repeat(16 * 1024 + 1)).statusCode());
    }

    @Test
    void devuelveCodigosCorrectosParaRutasMetodosParametrosYDuplicados() throws Exception {
        HttpResponse<String> metodo = enviar("PUT", RUTA, "Bearer medico-1", null, null);
        assertEquals(405, metodo.statusCode());
        assertEquals("GET, POST", metodo.headers().firstValue("Allow").orElse(""));
        assertEquals(405, enviar("POST", RUTA + "/id", "Bearer medico-1", null, null).statusCode());
        assertEquals(404, enviar("GET", RUTA + "/desconocida", "Bearer medico-1", null, null).statusCode());
        assertEquals(404, enviar("GET", RUTA + "-extra", "Bearer medico-1", null, null).statusCode());
        assertEquals(400, enviar("GET", RUTA + "?medicoId=otro", "Bearer medico-1", null, null).statusCode());

        String solicitud = cuerpo("p", "RM", "I", "J", "ORDINARIA");
        assertEquals(201, enviar("POST", RUTA, "Bearer medico-1", "application/json", solicitud).statusCode());
        siguienteId.set(0);
        HttpResponse<String> duplicada =
                enviar("POST", RUTA, "Bearer medico-1", "application/json", solicitud);
        assertEquals(409, duplicada.statusCode());
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
            String pacienteId, String tipo, String indicacion, String justificacion, String prioridad) {
        return "{\"pacienteId\":" + cadena(pacienteId)
                + ",\"tipoPrueba\":" + cadena(tipo)
                + ",\"indicacionClinica\":" + cadena(indicacion)
                + ",\"justificacion\":" + cadena(justificacion)
                + ",\"prioridad\":" + cadena(prioridad) + "}";
    }

    private static String cadena(String valor) {
        return "\"" + valor.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
