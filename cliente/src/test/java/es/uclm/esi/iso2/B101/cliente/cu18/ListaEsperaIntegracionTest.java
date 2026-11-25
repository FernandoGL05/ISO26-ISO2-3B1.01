package es.uclm.esi.iso2.B101.cliente.cu18;

import static org.junit.jupiter.api.Assertions.*;

import es.uclm.esi.iso2.B101.servidor.api.AplicacionServidor;
import es.uclm.esi.iso2.B101.servidor.api.JsonUtil;
import es.uclm.esi.iso2.B101.servidor.api.ServidorHTTP;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListaEsperaIntegracionTest {
    private ServidorHTTP servidor;
    private RepositorioCitasMemoria citas;
    private HttpClient clienteHttp;
    private String urlBase;

    @BeforeEach
    void preparar() throws Exception {
        Clock reloj = Clock.fixed(
                Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);

        citas = new RepositorioCitasMemoria();
        servidor = AplicacionServidor.crearServidor(0, reloj, citas);
        servidor.iniciar();

        urlBase = "http://127.0.0.1:" + servidor.getPuerto();

        clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void cerrar() {
        if (clienteHttp != null) {
            clienteHttp.close();
        }
        if (servidor != null) {
            servidor.detener();
        }
    }

    @Test
    void completaElFlujoConLoginRealYCitasCompartidas() throws Exception {
        String token = iniciarSesion("admision1", "admision123");

        try (var cliente = new ClienteListaEsperaHttp(urlBase, token)) {
            var ordinaria = cliente.inscribir(
                    "paciente-2", "medico-1", "TAC", "ORDINARIA");
            var urgente = cliente.inscribir(
                    "paciente-1", "medico-1", "TAC", "URGENTE");

            assertEquals(201, ordinaria.codigoHttp());
            assertEquals(201, urgente.codigoHttp());

            String idOrdinaria = JsonUtil.parsearObjetoPlano(
                    ordinaria.cuerpoJson()).get("id");
            String idUrgente = JsonUtil.parsearObjetoPlano(
                    urgente.cuerpoJson()).get("id");

            assertNotNull(idOrdinaria);
            assertNotNull(idUrgente);

            var pendientes = cliente.consultarPendientes(
                    "medico-1", "TAC");

            assertEquals(200, pendientes.codigoHttp());

            int posicionUrgente = pendientes.cuerpoJson().indexOf(idUrgente);
            int posicionOrdinaria = pendientes.cuerpoJson().indexOf(idOrdinaria);

            assertTrue(posicionUrgente >= 0);
            assertTrue(posicionOrdinaria > posicionUrgente);

            var asignacion = cliente.asignarSiguiente(
                    "medico-1", "TAC",
                    LocalDateTime.of(2026, 12, 3, 10, 0), 45);

            assertEquals(201, asignacion.codigoHttp());

            var citasMedico = citas.buscarPorMedicoId("medico-1");
            assertEquals(1, citasMedico.size());

            var cita = citasMedico.get(0);

            assertEquals("paciente-1", cita.getPacienteId());
            assertEquals(45, cita.getDuracionMinutos().orElseThrow());
            assertTrue(asignacion.cuerpoJson().contains(cita.getId()));

            var entradaCitada = cliente.consultarEntrada(idUrgente);
            assertEquals(200, entradaCitada.codigoHttp());

            var campos = JsonUtil.parsearObjetoPlano(
                    entradaCitada.cuerpoJson());

            assertEquals("CITADA", campos.get("estado"));
            assertEquals(cita.getId(), campos.get("citaId"));

            // El paciente consulta la cita por la API existente del CU02.
            String tokenPaciente = iniciarSesion(
                    "paciente1", "paciente123");

            var consultaPaciente = pedir(
                    "GET", "/api/citas", null, tokenPaciente);

            assertEquals(200, consultaPaciente.statusCode());
            assertTrue(consultaPaciente.body().contains(cita.getId()));

            var cambio = cliente.cambiarPrioridad(
                    idOrdinaria, "PREFERENTE");

            assertEquals(200, cambio.codigoHttp());
            assertEquals(
                    "PREFERENTE",
                    JsonUtil.parsearObjetoPlano(
                            cambio.cuerpoJson()).get("prioridad"));

            var cancelacion = cliente.cancelar(idOrdinaria);

            assertEquals(200, cancelacion.codigoHttp());
            assertEquals(
                    "CANCELADA",
                    JsonUtil.parsearObjetoPlano(
                            cancelacion.cuerpoJson()).get("estado"));

            var listaFinal = cliente.consultarPendientes();
            assertEquals(200, listaFinal.codigoHttp());
            assertEquals("[]", listaFinal.cuerpoJson());

            var historial = cliente.consultarPorPaciente("paciente-2");
            assertEquals(200, historial.codigoHttp());
            assertTrue(historial.cuerpoJson().contains(idOrdinaria));
            assertTrue(historial.cuerpoJson().contains("\"CANCELADA\""));
        }
    }

    @Test
    void exigeSesionYRolAdmisionEnLaAplicacionReal() throws Exception {
        var sinSesion = pedir(
                "GET", "/api/lista-espera", null, null);

        assertEquals(401, sinSesion.statusCode());

        try (var cliente = new ClienteListaEsperaHttp(
                urlBase, "token-inexistente")) {
            assertEquals(
                    401, cliente.consultarPendientes().codigoHttp());
        }

        String[][] credenciales = {
            {"paciente1", "paciente123"},
            {"medico1", "medico123"},
            {"admin1", "admin123"}
        };

        for (String[] credencial : credenciales) {
            String token = iniciarSesion(credencial[0], credencial[1]);

            try (var cliente = new ClienteListaEsperaHttp(urlBase, token)) {
                assertEquals(
                        403, cliente.consultarPendientes().codigoHttp());

                assertEquals(
                        403,
                        cliente.inscribir(
                                "paciente-1", "medico-1",
                                "TAC", "URGENTE").codigoHttp());
            }
        }

        String tokenAdmision = iniciarSesion(
                "admision1", "admision123");

        try (var cliente = new ClienteListaEsperaHttp(
                urlBase, tokenAdmision)) {
            var respuesta = cliente.consultarPendientes();

            assertEquals(200, respuesta.codigoHttp());
            assertEquals("[]", respuesta.cuerpoJson());
        }

        assertTrue(citas.buscarPorMedicoId("medico-1").isEmpty());
    }

    @Test
    void cerrarSesionInvalidaElAccesoDelCliente() throws Exception {
        String token = iniciarSesion("admision1", "admision123");

        try (var cliente = new ClienteListaEsperaHttp(urlBase, token)) {
            assertEquals(
                    200, cliente.consultarPendientes().codigoHttp());

            var cierre = pedir(
                    "POST", "/api/auth/logout", null, token);

            assertEquals(200, cierre.statusCode());

            var consulta = cliente.consultarPendientes();

            assertEquals(401, consulta.codigoHttp());
            assertFalse(consulta.esExito());

            var alta = cliente.inscribir(
                    "paciente-1", "medico-1", "TAC", "URGENTE");

            assertEquals(401, alta.codigoHttp());
            assertFalse(alta.cuerpoJson().contains(token));
        }

        // Otra sesion valida comprueba que el alta rechazada no se guardo.
        String nuevoToken = iniciarSesion("admision1", "admision123");

        try (var cliente = new ClienteListaEsperaHttp(urlBase, nuevoToken)) {
            var consulta = cliente.consultarPendientes();

            assertEquals(200, consulta.codigoHttp());
            assertEquals("[]", consulta.cuerpoJson());
        }
    }

    private String iniciarSesion(
            String usuario, String contrasena) throws Exception {
        String cuerpo = JsonUtil.aJson(java.util.Map.of(
                "username", usuario,
                "password", contrasena));

        var respuesta = pedir(
                "POST", "/api/auth/login", cuerpo, null);

        assertEquals(
                200, respuesta.statusCode(),
                "Debe permitir el login del usuario de prueba");

        String token = JsonUtil.parsearObjetoPlano(
                respuesta.body()).get("token");

        assertNotNull(token);
        assertFalse(token.isBlank());

        return token;
    }

    private HttpResponse<String> pedir(
            String metodo,
            String ruta,
            String cuerpo,
            String token) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder()
                .uri(URI.create(urlBase + ruta))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");

        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }

        if (cuerpo != null) {
            peticion.header(
                    "Content-Type", "application/json; charset=utf-8");
        }

        peticion.method(
                metodo,
                cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(
                                cuerpo, StandardCharsets.UTF_8));

        return clienteHttp.send(
                peticion.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}