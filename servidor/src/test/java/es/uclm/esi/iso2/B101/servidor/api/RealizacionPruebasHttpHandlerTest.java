package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioRealizacionPruebas;
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

class RealizacionPruebasHttpHandlerTest {
    private static final String RUTA = "/api/realizaciones-prueba";
    private static final String MEDICO_1 = "medico-1";
    private static final String MEDICO_2 = "medico-2";
    private static final String SIN_ROL = "sin-rol";

    private HttpServer servidor;
    private URI baseUri;
    private HttpClient cliente;
    private RepositorioCitasMemoria citas;
    private RepositorioHistorialPruebasMemoria historial;
    private RepositorioIncidenciasMemoria incidencias;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        citas = new RepositorioCitasMemoria();
        citas.guardar(new Cita(
                "cita-1", "paciente-1", "medico-1", "RM", LocalDateTime.of(2026, 11, 22, 9, 0)));
        citas.guardar(new Cita(
                "cita-2", "paciente-2", "medico-2", "TAC", LocalDateTime.of(2026, 11, 22, 9, 0)));
        historial = new RepositorioHistorialPruebasMemoria();
        incidencias = new RepositorioIncidenciasMemoria();
        ServicioRealizacionPruebas servicio =
                new ServicioRealizacionPruebas(citas, historial, incidencias);
        AutorizadorPrescripcionesDiagnosticas autorizador = cabecera -> switch (cabecera) {
            case "Bearer " + MEDICO_1 -> Optional.of(
                    new AutorizadorPrescripcionesDiagnosticas.IdentidadMedico(true, "medico-1"));
            case "Bearer " + MEDICO_2 -> Optional.of(
                    new AutorizadorPrescripcionesDiagnosticas.IdentidadMedico(true, "medico-2"));
            case "Bearer " + SIN_ROL -> Optional.of(
                    new AutorizadorPrescripcionesDiagnosticas.IdentidadMedico(false, "usuario-1"));
            default -> Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        RealizacionPruebasHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        baseUri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        cliente = HttpClient.newHttpClient();
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void registraPruebaRealizadaYActualizaSuCita() throws Exception {
        HttpResponse<String> respuesta = enviar(
                "POST", RUTA, MEDICO_1, "{\"citaId\":\"cita-1\",\"resultado\":\"REALIZADA\"}");

        assertEquals(201, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"citaId\":\"cita-1\""));
        assertTrue(respuesta.body().contains("\"tipoPrueba\":\"RM\""));
        assertEquals("REALIZADA", citas.buscarPorId("cita-1").orElseThrow().getEstado().name());
        assertEquals(1, historial.buscarPorPacienteId("paciente-1").size());
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }

    @Test
    void registraIncidenciaDeRealizacionEnElCualculoDeIncidenciasDelPaciente() throws Exception {
        HttpResponse<String> respuesta = enviar(
                "POST",
                RUTA,
                MEDICO_1,
                "{\"citaId\":\"cita-1\",\"resultado\":\"INCIDENCIA\","
                        + "\"descripcion\":\"Fallo del equipo durante la adquisición\"}");

        assertEquals(201, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"citaId\":\"cita-1\""));
        assertTrue(respuesta.body().contains("\"asunto\":\"Incidencia durante realización de prueba\""));
        assertEquals(1, incidencias.buscarPorPacienteId("paciente-1").size());
        assertTrue(historial.buscarPorCitaId("cita-1").isEmpty());
    }

    @Test
    void exigeAutenticacionMedicaYAccesoSobreLaCitaAsignada() throws Exception {
        assertEquals(401, enviar("POST", RUTA, null, "{}").statusCode());
        assertEquals(403, enviar("POST", RUTA, SIN_ROL, "{}").statusCode());
        assertEquals(404, enviar(
                "POST", RUTA, MEDICO_1, "{\"citaId\":\"cita-2\",\"resultado\":\"REALIZADA\"}").statusCode());
        assertEquals(404, enviar(
                "POST", RUTA, MEDICO_1, "{\"citaId\":\"desconocida\",\"resultado\":\"REALIZADA\"}").statusCode());
        assertEquals(405, enviar("GET", RUTA, MEDICO_1, null).statusCode());
    }

    @Test
    void rechazaJsonInvalidosResultadosInvalidosYRealizacionesDuplicadas() throws Exception {
        assertEquals(400, enviar("POST", RUTA, MEDICO_1, "{").statusCode());
        assertEquals(400, enviar(
                "POST", RUTA, MEDICO_1, "{\"citaId\":\"cita-1\",\"resultado\":\"OTRO\"}").statusCode());
        assertEquals(400, enviar(
                "POST", RUTA, MEDICO_1, "{\"citaId\":\"cita-1\",\"resultado\":\"INCIDENCIA\"}").statusCode());
        assertEquals(201, enviar(
                "POST", RUTA, MEDICO_1, "{\"citaId\":\"cita-1\",\"resultado\":\"REALIZADA\"}").statusCode());
        assertEquals(409, enviar(
                "POST", RUTA, MEDICO_1, "{\"citaId\":\"cita-1\",\"resultado\":\"REALIZADA\"}").statusCode());
        assertFalse(enviar("POST", RUTA, MEDICO_1, "{\"citaId\":\"cita-1\",\"resultado\":\"REALIZADA\"}")
                .body().contains("paciente-2"));
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String token, String cuerpo) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(baseUri.resolve(ruta))
                .header("Accept", "application/json");
        if (token != null) {
            solicitud.header("Authorization", "Bearer " + token);
        }
        if (cuerpo != null) {
            solicitud.header("Content-Type", "application/json");
        }
        HttpRequest.BodyPublisher contenido = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8);
        return cliente.send(
                solicitud.method(metodo, contenido).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
