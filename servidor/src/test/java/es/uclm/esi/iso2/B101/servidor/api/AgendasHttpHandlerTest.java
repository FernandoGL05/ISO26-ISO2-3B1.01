package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAgendas;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgendasHttpHandlerTest {
    private static final String TOKEN_MEDICO = "token-medico";
    private static final String TOKEN_OTRO_ROL = "token-otro-rol";
    private static final String CONSULTA =
            "?tipoPrueba=RM&desde=2026-12-01T09%3A00&hasta=2026-12-01T10%3A00&duracionMinutos=30";

    private HttpServer servidor;
    private URI baseUri;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        RepositorioCitasMemoria repositorio = new RepositorioCitasMemoria();
        repositorio.guardar(new Cita(
                "cita-1",
                "paciente-1",
                "medico-1",
                "TAC",
                LocalDateTime.of(2026, 12, 1, 9, 30)));
        ServicioAgendas servicio = new ServicioAgendas(repositorio);
        AutorizadorPrescripcionesDiagnosticas autorizador = cabecera -> switch (cabecera) {
            case "Bearer " + TOKEN_MEDICO -> Optional.of(
                    new AutorizadorPrescripcionesDiagnosticas.IdentidadMedico(true, "medico-1"));
            case "Bearer " + TOKEN_OTRO_ROL -> Optional.of(
                    new AutorizadorPrescripcionesDiagnosticas.IdentidadMedico(false, "usuario-1"));
            default -> Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AgendasHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        baseUri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void devuelveHuecosDeLaAgendaDelMedicoAutenticado() throws Exception {
        HttpResponse<String> respuesta = enviar("/api/agendas/disponibilidad" + CONSULTA, TOKEN_MEDICO);

        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"tipoPrueba\":\"RM\""));
        assertTrue(respuesta.body().contains("\"desde\":\"2026-12-01T09:00\",\"hasta\":\"2026-12-01T09:30\""));
        assertFalse(respuesta.body().contains("\"desde\":\"2026-12-01T09:30\""));
    }

    @Test
    void exigeSesionValidaYRolMedico() throws Exception {
        assertEquals(401, enviar("/api/agendas/disponibilidad" + CONSULTA, null).statusCode());
        HttpResponse<String> respuesta = enviar("/api/agendas/disponibilidad" + CONSULTA, TOKEN_OTRO_ROL);

        assertEquals(403, respuesta.statusCode());
        assertTrue(respuesta.body().contains("MEDICO"));
    }

    @Test
    void rechazaParametrosDuplicadosOIncompletos() throws Exception {
        assertEquals(
                400,
                enviar("/api/agendas/disponibilidad?tipoPrueba=RM", TOKEN_MEDICO).statusCode());
        assertEquals(
                400,
                enviar("/api/agendas/disponibilidad" + CONSULTA + "&desde=2026-12-01T09%3A00", TOKEN_MEDICO)
                        .statusCode());
    }

    private HttpResponse<String> enviar(String ruta, String token) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(baseUri.resolve(ruta)).GET();
        if (token != null) {
            solicitud.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(solicitud.build(), HttpResponse.BodyHandlers.ofString());
    }
}
