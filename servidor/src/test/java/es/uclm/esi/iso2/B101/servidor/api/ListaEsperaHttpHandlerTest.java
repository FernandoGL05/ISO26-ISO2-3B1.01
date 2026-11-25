package es.uclm.esi.iso2.B101.servidor.api;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoEntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioListaEsperaMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion.IdentidadAutenticada;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAsignacionListaEspera;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioListaEspera;

class ListaEsperaHttpHandlerTest {
    private static final String BASE = "/api/lista-espera";
    private static final String TOKEN = "Bearer admision-test";

    private static final String ALTA =
            "{\"pacienteId\":\"paciente-1\","
            + "\"medicoId\":\"medico-1\","
            + "\"tipoPrueba\":\"TAC\","
            + "\"prioridad\":\"ORDINARIA\"}";

    private static final String ASIGNACION =
            "{\"medicoId\":\"medico-1\","
            + "\"tipoPrueba\":\"TAC\","
            + "\"fechaHora\":\"2026-12-03T10:00\","
            + "\"duracionMinutos\":30}";

    private HttpServer servidor;
    private HttpClient cliente;
    private String urlBase;
    private ServicioListaEspera gestion;
    private RepositorioCitasMemoria citas;

    @BeforeEach
    void preparar() throws Exception {
        Clock reloj = Clock.fixed(
                Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);

        var lista = new RepositorioListaEsperaMemoria();
        citas = new RepositorioCitasMemoria();
        gestion = new ServicioListaEspera(lista, reloj);

        var asignacion = new ServicioAsignacionListaEspera(
                lista, citas, reloj);

        AutorizadorListaEspera autorizador = cabecera -> {
            if (TOKEN.equals(cabecera)) {
                return Optional.of(
                        new IdentidadAutenticada("admision-1", Rol.ADMISION));
            }
            if ("Bearer paciente-test".equals(cabecera)) {
                return Optional.of(
                        new IdentidadAutenticada("paciente-1", Rol.PACIENTE));
            }
            return Optional.empty();
        };

        servidor = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        ListaEsperaHttpHandler.registrarEn(
                servidor, gestion, asignacion, autorizador);

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
    void creaYConsultaUnaEntrada() throws Exception {
        var alta = pedir("POST", BASE, ALTA);

        assertEquals(201, alta.statusCode());

        EntradaListaEspera entrada = gestion.consultarPendientes().get(0);
        assertEquals(JsonListaEspera.entrada(entrada), alta.body());

        var consulta = pedir("GET", BASE + "/" + entrada.getId(), null);

        assertEquals(200, consulta.statusCode());
        assertEquals(alta.body(), consulta.body());
        assertEquals(
                "no-store",
                consulta.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void consultaPendientesYAplicaFiltros() throws Exception {
        EntradaListaEspera primera = inscribir(
                "paciente-1", PrioridadPrueba.URGENTE);

        gestion.inscribir(
                "paciente-2", "medico-2", "RM",
                PrioridadPrueba.ORDINARIA);

        var todas = pedir("GET", BASE, null);
        var porPaciente = pedir(
                "GET", BASE + "?pacienteId=paciente-1", null);
        var porMedico = pedir(
                "GET", BASE + "?medicoId=medico-1&tipoPrueba=TAC", null);

        assertEquals(200, todas.statusCode());
        assertEquals(
                JsonListaEspera.entradas(gestion.consultarPendientes()),
                todas.body());
        assertEquals(200, porPaciente.statusCode());
        assertEquals(
                JsonListaEspera.entradas(List.of(primera)),
                porPaciente.body());
        assertEquals(200, porMedico.statusCode());
        assertEquals(porPaciente.body(), porMedico.body());
    }

    @Test
    void cambiaPrioridadYCancelaConservandoElHistorial() throws Exception {
        EntradaListaEspera entrada = inscribir(
                "paciente-1", PrioridadPrueba.ORDINARIA);
        String ruta = BASE + "/" + entrada.getId();

        var cambio = pedir(
                "PUT", ruta + "/prioridad",
                "{\"prioridad\":\"URGENTE\"}");

        assertEquals(200, cambio.statusCode());
        assertEquals(
                PrioridadPrueba.URGENTE,
                gestion.consultarEntrada(entrada.getId()).getPrioridad());

        var cancelacion = pedir("POST", ruta + "/cancelacion", null);

        assertEquals(200, cancelacion.statusCode());
        assertEquals(
                EstadoEntradaListaEspera.CANCELADA,
                gestion.consultarEntrada(entrada.getId()).getEstado());
        assertTrue(gestion.consultarPendientes().isEmpty());
        assertEquals(1, gestion.consultarPorPaciente("paciente-1").size());

        assertEquals(
                409,
                pedir("POST", ruta + "/cancelacion", null).statusCode());
    }

    @Test
    void asignaLaSiguienteEntradaSegunPrioridad() throws Exception {
        inscribir("paciente-1", PrioridadPrueba.ORDINARIA);
        EntradaListaEspera urgente = inscribir(
                "paciente-2", PrioridadPrueba.URGENTE);

        var respuesta = pedir("POST", BASE + "/asignaciones", ASIGNACION);

        assertEquals(201, respuesta.statusCode());

        EntradaListaEspera citada =
                gestion.consultarEntrada(urgente.getId());

        assertEquals(EstadoEntradaListaEspera.CITADA, citada.getEstado());

        Cita cita = citas.buscarPorId(citada.getCitaId()).orElseThrow();

        assertEquals("paciente-2", cita.getPacienteId());
        assertEquals(30, cita.getDuracionMinutos().orElseThrow());
        assertTrue(respuesta.body().contains(citada.getId()));
        assertTrue(respuesta.body().contains(cita.getId()));
        assertEquals(1, gestion.consultarPendientes().size());
    }

    @Test
    void rechazaEntradasDuplicadas() throws Exception {
        assertEquals(201, pedir("POST", BASE, ALTA).statusCode());
        assertEquals(409, pedir("POST", BASE, ALTA).statusCode());
        assertEquals(1, gestion.consultarPendientes().size());
    }

    @Test
    void exigeUnaSesionValida() throws Exception {
        var sinToken = pedir(
                "POST", BASE, ALTA, null, "application/json");
        var tokenInvalido = pedir(
                "POST", BASE, ALTA,
                "Bearer desconocido", "application/json");

        assertEquals(401, sinToken.statusCode());
        assertEquals(401, tokenInvalido.statusCode());
        assertEquals(
                "Bearer",
                sinToken.headers()
                        .firstValue("WWW-Authenticate").orElseThrow());
        assertTrue(gestion.consultarPendientes().isEmpty());
    }

    @Test
    void rechazaElRolPaciente() throws Exception {
        var respuesta = pedir(
                "POST", BASE, ALTA,
                "Bearer paciente-test", "application/json");

        assertEquals(403, respuesta.statusCode());
        assertTrue(gestion.consultarPendientes().isEmpty());
    }

    @Test
    void devuelve404ParaEntradasRutasOCandidatosInexistentes()
            throws Exception {
        assertEquals(
                404,
                pedir("GET", BASE + "/inexistente", null).statusCode());
        assertEquals(
                404,
                pedir("GET", BASE + "/uno/ruta-invalida", null).statusCode());
        assertEquals(
                404,
                pedir("POST", BASE + "/asignaciones", ASIGNACION)
                        .statusCode());
    }

    @Test
    void rechazaMetodosNoPermitidos() throws Exception {
        var respuesta = pedir("DELETE", BASE, null);

        assertEquals(405, respuesta.statusCode());
        assertEquals(
                "GET, POST",
                respuesta.headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void rechazaJsonInvalidoSinCrearEntradas() throws Exception {
        String[] cuerpos = {
            "",
            "{}",
            "null",
            "[]",
            ALTA + " texto",
            ALTA.replace(",\"medicoId\"", "\"medicoId\""),
            ALTA.replace(
                    "\"pacienteId\":\"paciente-1\"",
                    "\"pacienteId\":12"),
            ALTA.replace(
                    "\"ORDINARIA\"", "\"INVENTADA\""),
            ALTA.replace(
                    "\"paciente-1\"", "null"),
            ALTA.substring(0, ALTA.length() - 1) + ",}",
            ALTA.substring(0, ALTA.length() - 1)
                    + ",\"prioridad\":\"URGENTE\"}",
            ALTA.substring(0, ALTA.length() - 1)
                    + ",\"extra\":\"valor\"}"
        };

        for (String cuerpo : cuerpos) {
            assertEquals(
                    400,
                    pedir("POST", BASE, cuerpo).statusCode(),
                    "Debe rechazar: " + cuerpo);
        }

        assertTrue(gestion.consultarPendientes().isEmpty());
    }

    @Test
    void exigeContentTypeJson() throws Exception {
        var respuesta = pedir(
                "POST", BASE, ALTA, TOKEN, "text/plain");

        assertEquals(415, respuesta.statusCode());
        assertTrue(gestion.consultarPendientes().isEmpty());
    }

    @Test
    void rechazaCuerposDemasiadoGrandes() throws Exception {
        var respuesta = pedir(
                "POST", BASE, "x".repeat(16 * 1024 + 1));

        assertEquals(413, respuesta.statusCode());
        assertTrue(gestion.consultarPendientes().isEmpty());
    }

    @Test
    void rechazaFiltrosInvalidosYRepetidos() throws Exception {
        String[] consultas = {
            "?medicoId=medico-1",
            "?tipoPrueba=TAC",
            "?pacienteId=",
            "?pacienteId=uno&pacienteId=dos",
            "?desconocido=valor",
            "?pacienteId=uno&medicoId=medico-1"
        };

        for (String consulta : consultas) {
            assertEquals(
                    400,
                    pedir("GET", BASE + consulta, null).statusCode());
        }
    }

    @Test
    void rechazaAsignacionEnUnIntervaloOcupado() throws Exception {
        EntradaListaEspera entrada = inscribir(
                "paciente-1", PrioridadPrueba.URGENTE);

        citas.guardar(new Cita(
                "previa", "otro-paciente", "medico-1", "RM",
                LocalDateTime.of(2026, 12, 3, 9, 45), 60));

        var respuesta = pedir(
                "POST", BASE + "/asignaciones", ASIGNACION);

        assertEquals(409, respuesta.statusCode());
        assertSame(entrada, gestion.consultarEntrada(entrada.getId()));
        assertTrue(citas.buscarPorPacienteId("paciente-1").isEmpty());
    }

    private EntradaListaEspera inscribir(
            String pacienteId, PrioridadPrueba prioridad) {
        return gestion.inscribir(
                pacienteId, "medico-1", "TAC", prioridad);
    }

    private HttpResponse<String> pedir(
            String metodo,
            String ruta,
            String cuerpo) throws Exception {
        return pedir(metodo, ruta, cuerpo, TOKEN, "application/json");
    }

    private HttpResponse<String> pedir(
            String metodo,
            String ruta,
            String cuerpo,
            String autorizacion,
            String contentType) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder()
                .uri(URI.create(urlBase + ruta))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json");

        if (autorizacion != null) {
            peticion.header("Authorization", autorizacion);
        }

        if (cuerpo != null) {
            peticion.header("Content-Type", contentType);
        }

        peticion.method(
                metodo,
                cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(
                                cuerpo, StandardCharsets.UTF_8));

        return cliente.send(
                peticion.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}