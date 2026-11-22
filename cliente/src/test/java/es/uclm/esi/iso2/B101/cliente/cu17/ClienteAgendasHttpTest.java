package es.uclm.esi.iso2.B101.cliente.cu17;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ClienteAgendasHttpTest {
    private static final String TOKEN = "admision-test";
    private static final LocalDate FECHA = LocalDate.of(2026, 12, 3);

    private HttpServer servidor;
    private ClienteAgendasHttp cliente;

    private final AtomicReference<Peticion> ultimaPeticion =
            new AtomicReference<>();

    private volatile int estadoRespuesta = 200;
    private volatile String cuerpoRespuesta = "{\"exito\":true}";

    @BeforeEach
    void preparar() throws IOException {
        servidor = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        servidor.createContext("/", this::responder);
        servidor.start();

        cliente = new ClienteAgendasHttp(
                "http://127.0.0.1:" + servidor.getAddress().getPort(),
                TOKEN);
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
    void creaAgendaConJsonYAutorizacion() throws Exception {
        estadoRespuesta = 201;

        ClienteAgendasHttp.ResultadoHttp respuesta =
                cliente.crearAgenda("medico-1", FECHA);

        assertEquals(201, respuesta.codigoHttp());
        assertTrue(respuesta.esExito());

        comprobarPeticion(
                "POST",
                "/api/agendas",
                null,
                "{\"medicoId\":\"medico-1\",\"fecha\":\"2026-12-03\"}");

        assertEquals(
                "application/json; charset=utf-8",
                ultimaPeticion.get().contentType());
    }

    @Test
    void consultaAgendaYFiltraPorFechaOMedico() throws Exception {
        cliente.consultarAgenda("agenda-1");
        comprobarPeticion(
                "GET", "/api/agendas/agenda-1", null, "");

        cliente.consultarAgendasPorFecha(FECHA);
        comprobarPeticion(
                "GET", "/api/agendas", "fecha=2026-12-03", "");

        cliente.consultarAgendaPorMedicoYFecha("medico-1", FECHA);
        comprobarPeticion(
                "GET",
                "/api/agendas",
                "medicoId=medico-1&fecha=2026-12-03",
                "");
    }

    @Test
    void enviaOperacionesDeHuecosConSusMetodos() throws Exception {
        LocalTime inicio = LocalTime.of(9, 0);
        LocalTime fin = LocalTime.of(9, 30);
        String cuerpo =
                "{\"horaInicio\":\"09:00\",\"horaFin\":\"09:30\"}";

        cliente.agregarHueco("agenda-1", inicio, fin);
        comprobarPeticion(
                "POST", "/api/agendas/agenda-1/huecos", null, cuerpo);

        cliente.consultarHuecos("agenda-1");
        comprobarPeticion(
                "GET", "/api/agendas/agenda-1/huecos", null, "");

        cliente.modificarHueco("agenda-1", "hueco-1", inicio, fin);
        comprobarPeticion(
                "PUT",
                "/api/agendas/agenda-1/huecos/hueco-1",
                null,
                cuerpo);

        cliente.eliminarHueco("agenda-1", "hueco-1");
        comprobarPeticion(
                "DELETE",
                "/api/agendas/agenda-1/huecos/hueco-1",
                null,
                "");
    }

    @Test
    void codificaIdentificadoresYParametrosDeConsulta() throws Exception {
        cliente.consultarAgenda("agenda +?");
        comprobarPeticion(
                "GET", "/api/agendas/agenda%20%2B%3F", null, "");

        cliente.consultarAgendaPorMedicoYFecha("medico +&", FECHA);
        comprobarPeticion(
                "GET",
                "/api/agendas",
                "medicoId=medico%20%2B%26&fecha=2026-12-03",
                "");
    }

    @Test
    void escapaCaracteresEspecialesDelJson() throws Exception {
        cliente.crearAgenda("medico\"\\\n", FECHA);

        assertEquals(
                "{\"medicoId\":\"medico\\\"\\\\\\n\","
                        + "\"fecha\":\"2026-12-03\"}",
                ultimaPeticion.get().cuerpo());
    }

    @Test
    void conservaCodigoYCuerpoDeErroresHttp() throws Exception {
        for (int codigo : new int[]{400, 401, 403, 404, 409, 500}) {
            estadoRespuesta = codigo;
            cuerpoRespuesta = "{\"codigoError\":\"ERROR_TEST\"}";

            ClienteAgendasHttp.ResultadoHttp respuesta =
                    cliente.consultarAgendasPorFecha(FECHA);

            assertEquals(codigo, respuesta.codigoHttp());
            assertEquals(cuerpoRespuesta, respuesta.cuerpoJson());
            assertFalse(respuesta.esExito());
            assertFalse(respuesta.cuerpoJson().contains(TOKEN));
        }
    }

    @Test
    void rechazaDatosInvalidosAntesDeEnviar() {
        assertThrows(IllegalArgumentException.class,
                () -> cliente.consultarAgenda(" "));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.consultarAgenda("../otra"));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.agregarHueco(
                        "agenda-1", LocalTime.NOON, LocalTime.NOON));

        assertThrows(IllegalArgumentException.class,
                () -> cliente.agregarHueco(
                        "agenda-1", null, LocalTime.NOON));

        assertThrows(IllegalArgumentException.class,
                () -> new ClienteAgendasHttp(
                        "http://127.0.0.1", "token con espacios"));

        assertThrows(IllegalArgumentException.class,
                () -> new ClienteAgendasHttp(
                        "ftp://127.0.0.1", TOKEN));

        assertNull(ultimaPeticion.get());
    }

    @Test
    void comunicaFalloDeConexionSinMostrarElToken() {
        servidor.stop(0);
        servidor = null;

        IOException error = assertThrows(
                IOException.class,
                () -> cliente.consultarAgendasPorFecha(FECHA));

        assertFalse(String.valueOf(error.getMessage()).contains(TOKEN));
    }

    private void responder(HttpExchange intercambio) throws IOException {
        try {
            ultimaPeticion.set(new Peticion(
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

            byte[] cuerpo =
                    cuerpoRespuesta.getBytes(StandardCharsets.UTF_8);

            intercambio.getResponseHeaders().set(
                    "Content-Type", "application/json; charset=utf-8");

            intercambio.sendResponseHeaders(
                    estadoRespuesta, cuerpo.length);

            try (var salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        } finally {
            intercambio.close();
        }
    }

    private void comprobarPeticion(
            String metodo,
            String ruta,
            String consulta,
            String cuerpo) {

        Peticion peticion = ultimaPeticion.get();
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