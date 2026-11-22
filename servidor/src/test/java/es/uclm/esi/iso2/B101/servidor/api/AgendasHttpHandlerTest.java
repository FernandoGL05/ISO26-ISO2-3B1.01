package es.uclm.esi.iso2.B101.servidor.api;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import es.uclm.esi.iso2.B101.servidor.dominio.AgendaDiaria;
import es.uclm.esi.iso2.B101.servidor.dominio.HuecoAgenda;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAgendasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHuecosAgendaMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAgendas;

class AgendasHttpHandlerTest {
    private HttpServer servidor;
    private ServicioAgendas servicio;
    private HttpClient cliente;
    private String urlBase;

    @BeforeEach
    void preparar() throws Exception {
        servicio = new ServicioAgendas(
                new RepositorioAgendasMemoria(),
                new RepositorioHuecosAgendaMemoria());

        servidor = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        AgendasHttpHandler.registrarEn(
                servidor, servicio, cabecera -> {
                    if ("Bearer admision-test".equals(cabecera)) {
                        return Optional.of(
                                new AutorizadorAgendas.IdentidadAutorizada(
                                        "admision-1", true));
                    }
                    if ("Bearer paciente-test".equals(cabecera)) {
                        return Optional.of(
                                new AutorizadorAgendas.IdentidadAutorizada(
                                        "paciente-1", false));
                    }
                    return Optional.empty();
                });

        servidor.start();
        urlBase = "http://127.0.0.1:"
                + servidor.getAddress().getPort();

        cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void cerrar() {
        if (cliente != null) {
            cliente.close();
        }
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void creaAgendaYPermiteConsultarla() throws Exception {
        HttpResponse<String> creacion = enviar(
                "POST", "/api/agendas",
                "{\"medicoId\":\"medico-1\",\"fecha\":\"2026-12-03\"}");

        assertEquals(201, creacion.statusCode());

        AgendaDiaria agenda =
                servicio.consultarAgendaPorMedicoYFecha(
                        "medico-1", LocalDate.of(2026, 12, 3));

        HttpResponse<String> consulta = enviar(
                "GET", "/api/agendas/" + agenda.getId(), null);

        assertEquals(200, consulta.statusCode());
        assertEquals(JsonAgendas.agenda(agenda), consulta.body());
        assertTrue(creacion.body().contains(agenda.getId()));
    }

    @Test
    void consultaPorFechaYPorMedico() throws Exception {
        AgendaDiaria agenda = crearAgenda();

        HttpResponse<String> listado = enviar(
                "GET", "/api/agendas?fecha=2026-12-03", null);

        assertEquals(200, listado.statusCode());
        assertEquals("[" + JsonAgendas.agenda(agenda) + "]",
                listado.body());

        HttpResponse<String> consulta = enviar(
                "GET",
                "/api/agendas?medicoId=medico-1&fecha=2026-12-03",
                null);

        assertEquals(200, consulta.statusCode());
        assertEquals(JsonAgendas.agenda(agenda), consulta.body());
    }

    @Test
    void creaModificaYEliminaHueco() throws Exception {
        AgendaDiaria agenda = crearAgenda();
        String ruta = "/api/agendas/" + agenda.getId() + "/huecos";

        HttpResponse<String> creacion = enviar(
                "POST", ruta,
                "{\"horaInicio\":\"09:00\",\"horaFin\":\"09:30\"}");

        assertEquals(201, creacion.statusCode());

        HuecoAgenda hueco =
                servicio.consultarHuecos(agenda.getId()).get(0);

        HttpResponse<String> cambio = enviar(
                "PUT", ruta + "/" + hueco.getId(),
                "{\"horaInicio\":\"10:00\",\"horaFin\":\"10:30\"}");

        assertEquals(200, cambio.statusCode());
        assertEquals(LocalTime.of(10, 0),
                servicio.consultarHuecos(agenda.getId())
                        .get(0).getHoraInicio());

        HttpResponse<String> listado = enviar("GET", ruta, null);
        assertEquals(200, listado.statusCode());
        assertTrue(listado.body().contains("10:30"));

        HttpResponse<String> borrado = enviar(
                "DELETE", ruta + "/" + hueco.getId(), null);

        assertEquals(200, borrado.statusCode());
        assertTrue(servicio.consultarHuecos(agenda.getId()).isEmpty());
    }

    @Test
    void rechazaAgendaDuplicadaCon409() throws Exception {
        crearAgenda();

        HttpResponse<String> respuesta = enviar(
                "POST", "/api/agendas",
                "{\"medicoId\":\"medico-1\",\"fecha\":\"2026-12-03\"}");

        assertEquals(409, respuesta.statusCode());
        assertEquals(1, servicio.consultarAgendasPorFecha(
                LocalDate.of(2026, 12, 3)).size());
    }

    @Test
    void rechazaSolapamientoCon409() throws Exception {
        AgendaDiaria agenda = crearAgenda();
        servicio.agregarHueco(
                agenda.getId(),
                LocalTime.of(9, 0), LocalTime.of(10, 0));

        HttpResponse<String> respuesta = enviar(
                "POST", "/api/agendas/" + agenda.getId() + "/huecos",
                "{\"horaInicio\":\"09:30\",\"horaFin\":\"10:30\"}");

        assertEquals(409, respuesta.statusCode());
        assertEquals(1, servicio.consultarHuecos(agenda.getId()).size());
    }

    @Test
    void rechazaSesionAusenteOInvalidaCon401() throws Exception {
        for (String autorizacion : new String[]{
                null, "Bearer desconocido", "Bearer ", "Basic abc"}) {

            HttpResponse<String> respuesta = enviar(
                    "GET", "/api/agendas?fecha=2026-12-03",
                    null, autorizacion, null);

            assertEquals(401, respuesta.statusCode());
        }
    }

    @Test
    void rechazaUsuarioSinRolAdmisionCon403() throws Exception {
        HttpResponse<String> respuesta = enviar(
                "POST", "/api/agendas",
                "{\"medicoId\":\"medico-1\",\"fecha\":\"2026-12-03\"}",
                "Bearer paciente-test", "application/json");

        assertEquals(403, respuesta.statusCode());
        assertTrue(servicio.consultarAgendasPorFecha(
                LocalDate.of(2026, 12, 3)).isEmpty());
    }

    @Test
    void devuelve404ParaAgendaInexistente() throws Exception {
        assertEquals(404, enviar(
                "GET", "/api/agendas/no-existe", null).statusCode());
    }

    @Test
    void rechazaMetodoIncorrectoCon405() throws Exception {
        HttpResponse<String> respuesta =
                enviar("DELETE", "/api/agendas", null);

        assertEquals(405, respuesta.statusCode());
        assertEquals("GET, POST",
                respuesta.headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void rechazaJsonInvalidoSinCrearAgendas() throws Exception {
        String[] cuerpos = {
                "{}",
                "{\"medicoId\":\"medico-1\"}",
                "{\"medicoId\":\"medico-1\",\"fecha\":\"incorrecta\"}",
                "{\"medicoId\":\"medico-1\",\"fecha\":null}",
                "{\"medicoId\":\"medico-1\",\"fecha\":\"2026-12-03\","
                        + "\"extra\":\"dato\"}",
                "{\"medicoId\":\"medico-1\",\"medicoId\":\"medico-2\","
                        + "\"fecha\":\"2026-12-03\"}"
        };

        for (String cuerpo : cuerpos) {
            assertEquals(400,
                    enviar("POST", "/api/agendas", cuerpo).statusCode());
        }

        assertTrue(servicio.consultarAgendasPorFecha(
                LocalDate.of(2026, 12, 3)).isEmpty());
    }

    @Test
    void rechazaTipoDeContenidoIncorrectoCon415() throws Exception {
        HttpResponse<String> respuesta = enviar(
                "POST", "/api/agendas", "{}",
                "Bearer admision-test", "text/plain");

        assertEquals(415, respuesta.statusCode());
    }

    @Test
    void rechazaCuerpoDemasiadoGrandeCon413() throws Exception {
        HttpResponse<String> respuesta = enviar(
                "POST", "/api/agendas", "x".repeat(16 * 1024 + 1));

        assertEquals(413, respuesta.statusCode());
    }

    @Test
    void rechazaParametrosDuplicadosCon400() throws Exception {
        HttpResponse<String> respuesta = enviar(
                "GET",
                "/api/agendas?fecha=2026-12-03&fecha=2026-12-04",
                null);

        assertEquals(400, respuesta.statusCode());
    }

    @Test
    void impideEliminarHuecoDeOtraAgenda() throws Exception {
        AgendaDiaria primera = crearAgenda();
        AgendaDiaria segunda = servicio.crearAgenda(
                "medico-2", LocalDate.of(2026, 12, 3));

        HuecoAgenda hueco = servicio.agregarHueco(
                primera.getId(),
                LocalTime.of(9, 0), LocalTime.of(10, 0));

        HttpResponse<String> respuesta = enviar(
                "DELETE",
                "/api/agendas/" + segunda.getId()
                        + "/huecos/" + hueco.getId(),
                null);

        assertEquals(404, respuesta.statusCode());
        assertEquals(1, servicio.consultarHuecos(primera.getId()).size());
    }

    private AgendaDiaria crearAgenda() {
        return servicio.crearAgenda(
                "medico-1", LocalDate.of(2026, 12, 3));
    }

    private HttpResponse<String> enviar(
            String metodo, String ruta, String cuerpo) throws Exception {

        return enviar(
                metodo, ruta, cuerpo,
                "Bearer admision-test",
                cuerpo == null ? null : "application/json");
    }

    private HttpResponse<String> enviar(
            String metodo,
            String ruta,
            String cuerpo,
            String autorizacion,
            String contentType) throws Exception {

        HttpRequest.Builder peticion = HttpRequest.newBuilder()
                .uri(URI.create(urlBase + ruta))
                .timeout(Duration.ofSeconds(5));

        if (autorizacion != null) {
            peticion.header("Authorization", autorizacion);
        }
        if (contentType != null) {
            peticion.header("Content-Type", contentType);
        }

        HttpRequest.BodyPublisher contenido = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(
                        cuerpo, StandardCharsets.UTF_8);

        return cliente.send(
                peticion.method(metodo, contenido).build(),
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8));
    }
}