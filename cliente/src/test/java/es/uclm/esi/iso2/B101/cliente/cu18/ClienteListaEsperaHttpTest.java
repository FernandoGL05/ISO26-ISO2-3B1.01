package es.uclm.esi.iso2.B101.cliente.cu18;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteListaEsperaHttpTest {
    private static final String TOKEN = "token-prueba-cu18";

    private HttpServer servidor;
    private ClienteListaEsperaHttp cliente;

    private final AtomicReference<Peticion> ultima =
            new AtomicReference<>();

    private volatile int estado = 200;
    private volatile String respuesta = "{\"exito\":true}";

    @BeforeEach
    void preparar() throws IOException {
        servidor = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        servidor.createContext("/", this::responder);
        servidor.start();

        String urlBase = "http://127.0.0.1:"
                + servidor.getAddress().getPort();

        cliente = new ClienteListaEsperaHttp(urlBase, TOKEN);
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
    void inscribeConJsonEscapadoYAutorizacion() throws Exception {
        estado = 201;

        var resultado = cliente.inscribir(
                "paciente-1", "medico-1", "TAC \"especial\"\n",
                "URGENTE");

        assertEquals(201, resultado.codigoHttp());
        assertTrue(resultado.esExito());

        comprobar(
                "POST", "/api/lista-espera", null,
                "{\"pacienteId\":\"paciente-1\","
                + "\"medicoId\":\"medico-1\","
                + "\"tipoPrueba\":\"TAC \\\"especial\\\"\\n\","
                + "\"prioridad\":\"URGENTE\"}");

        assertEquals(
                "application/json; charset=utf-8",
                ultima.get().contentType());
    }

    @Test
    void consultaListaEntradaYFiltros() throws Exception {
        var resultado = cliente.consultarPendientes();

        assertEquals(200, resultado.codigoHttp());
        assertEquals(respuesta, resultado.cuerpoJson());
        comprobar("GET", "/api/lista-espera", null, "");

        cliente.consultarEntrada("entrada-1");
        comprobar("GET", "/api/lista-espera/entrada-1", null, "");

        cliente.consultarPorPaciente("paciente-1");
        comprobar(
                "GET", "/api/lista-espera",
                "pacienteId=paciente-1", "");

        cliente.consultarPendientes("medico-1", "TAC");
        comprobar(
                "GET", "/api/lista-espera",
                "medicoId=medico-1&tipoPrueba=TAC", "");
    }

    @Test
    void cambiaPrioridadYCancelaConLosMetodosCorrectos() throws Exception {
        cliente.cambiarPrioridad("entrada-1", "PREFERENTE");

        comprobar(
                "PUT", "/api/lista-espera/entrada-1/prioridad",
                null, "{\"prioridad\":\"PREFERENTE\"}");

        cliente.cancelar("entrada-1");

        comprobar(
                "POST", "/api/lista-espera/entrada-1/cancelacion",
                null, "");
    }

    @Test
    void enviaLaAsignacionConFechaYDuracionNumerica() throws Exception {
        estado = 201;

        var resultado = cliente.asignarSiguiente(
                "medico-1", "TAC",
                LocalDateTime.of(2026, 12, 3, 10, 0), 45);

        assertEquals(201, resultado.codigoHttp());
        assertTrue(resultado.esExito());

        comprobar(
                "POST", "/api/lista-espera/asignaciones", null,
                "{\"medicoId\":\"medico-1\","
                + "\"tipoPrueba\":\"TAC\","
                + "\"fechaHora\":\"2026-12-03T10:00\","
                + "\"duracionMinutos\":45}");
    }

    @Test
    void codificaIdentificadoresYParametros() throws Exception {
        cliente.consultarEntrada("entrada +?");

        comprobar(
                "GET", "/api/lista-espera/entrada%20%2B%3F",
                null, "");

        cliente.consultarPorPaciente("paciente +&");

        comprobar(
                "GET", "/api/lista-espera",
                "pacienteId=paciente%20%2B%26", "");

        cliente.consultarPendientes("medico /+", "TAC & RM");

        comprobar(
                "GET", "/api/lista-espera",
                "medicoId=medico%20%2F%2B&tipoPrueba=TAC%20%26%20RM",
                "");
    }

    @Test
    void conservaLosCodigosYCuerposDeError() throws Exception {
        respuesta = "{\"exito\":false,\"codigoError\":\"ERROR_TEST\"}";

        for (int codigo : new int[] {
                400, 401, 403, 404, 405, 409, 413, 415, 500
        }) {
            estado = codigo;

            var resultado = cliente.consultarPendientes();

            assertEquals(codigo, resultado.codigoHttp());
            assertEquals(respuesta, resultado.cuerpoJson());
            assertFalse(resultado.esExito());
            assertFalse(resultado.cuerpoJson().contains(TOKEN));
        }
    }

    @Test
    void rechazaDatosInvalidosAntesDeEnviar() {
        assertThrows(IllegalArgumentException.class,
                () -> cliente.inscribir(
                        " ", "medico-1", "TAC", "URGENTE"));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.inscribir(
                        "paciente-1", "medico-1", "TAC", "INVENTADA"));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.consultarEntrada("../entrada"));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.cancelar(""));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.cambiarPrioridad("entrada-1", null));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.consultarPorPaciente(" "));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.consultarPendientes("medico-1", ""));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.asignarSiguiente(
                        "medico-1", "TAC", null, 30));

        for (int duracion : new int[] {-1, 0, 1441}) {
            assertThrows(IllegalArgumentException.class,
                    () -> cliente.asignarSiguiente(
                            "medico-1", "TAC",
                            LocalDateTime.of(2026, 12, 3, 10, 0),
                            duracion));
        }

        assertNull(ultima.get());
    }

    @Test
    void comunicaElErrorDeConexionSinMostrarElToken() {
        servidor.stop(0);
        servidor = null;

        IOException error = assertThrows(
                IOException.class, () -> cliente.consultarPendientes());

        assertFalse(String.valueOf(error.getMessage()).contains(TOKEN));
    }

    private void responder(HttpExchange intercambio) throws IOException {
        try {
            ultima.set(new Peticion(
                    intercambio.getRequestMethod(),
                    intercambio.getRequestURI().getRawPath(),
                    intercambio.getRequestURI().getRawQuery(),
                    intercambio.getRequestHeaders()
                            .getFirst("Authorization"),
                    intercambio.getRequestHeaders()
                            .getFirst("Content-Type"),
                    new String(
                            intercambio.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8)));

            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);

            intercambio.getResponseHeaders().set(
                    "Content-Type", "application/json; charset=utf-8");

            intercambio.sendResponseHeaders(estado, bytes.length);

            try (var salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        } finally {
            intercambio.close();
        }
    }

    private void comprobar(
            String metodo,
            String ruta,
            String consulta,
            String cuerpo) {
        Peticion peticion = ultima.get();

        assertNotNull(peticion);
        assertEquals(metodo, peticion.metodo());
        assertEquals(ruta, peticion.ruta());
        assertEquals(consulta, peticion.consulta());
        assertEquals("Bearer " + TOKEN, peticion.autorizacion());
        assertEquals(cuerpo, peticion.cuerpo());
    }

    private record Peticion(
            String metodo,
            String ruta,
            String consulta,
            String autorizacion,
            String contentType,
            String cuerpo) {
    }
}