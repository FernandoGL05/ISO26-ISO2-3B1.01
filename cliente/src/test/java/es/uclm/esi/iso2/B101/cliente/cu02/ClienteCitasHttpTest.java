package es.uclm.esi.iso2.B101.cliente.cu02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteCitasHttpTest {
    private static final String TOKEN = "token-de-prueba-sensible";
    private HttpServer servidorPrueba;
    private String urlBase;
    private ClienteCitasHttp cliente;
    private final AtomicReference<PeticionRecibida> ultimaPeticion = new AtomicReference<>();
    private volatile int estadoRespuesta = 200;
    private volatile String cuerpoRespuesta = "{\"exito\":true}";

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        servidorPrueba = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidorPrueba.createContext("/", this::responder);
        servidorPrueba.start();
        urlBase = "http://127.0.0.1:" + servidorPrueba.getAddress().getPort();
        cliente = new ClienteCitasHttp(urlBase, TOKEN);
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidorPrueba != null) {
            servidorPrueba.stop(0);
        }
    }

    @Test
    void consultaCitasPorGetYEnviaToken() throws Exception {
        ClienteCitasHttp.ResultadoHttp respuesta = cliente.consultarCitas();

        assertEquals(200, respuesta.codigoHttp());
        assertEquals("{\"exito\":true}", respuesta.cuerpoJson());
        assertTrue(respuesta.esExito());
        assertUltimaPeticion("GET", "/api/citas", "");
    }

    @Test
    void confirmaCitaPorPostYCodificaElIdentificador() throws Exception {
        ClienteCitasHttp.ResultadoHttp respuesta = cliente.confirmarCita("cita /+?");

        assertEquals(200, respuesta.codigoHttp());
        assertTrue(respuesta.esExito());
        assertUltimaPeticion("POST", "/api/citas/cita%20%2F%2B%3F/confirmacion", "");
    }

    @Test
    void solicitaCambioConJsonYFechaIso8601() throws Exception {
        LocalDateTime nuevaFechaHora = LocalDateTime.of(2026, 12, 3, 12, 0);
        estadoRespuesta = 201;

        ClienteCitasHttp.ResultadoHttp respuesta =
                cliente.solicitarCambioFecha("cita-1", nuevaFechaHora);

        assertEquals(201, respuesta.codigoHttp());
        assertTrue(respuesta.esExito());
        assertUltimaPeticion("POST", "/api/citas/cita-1/solicitudes/cambio",
                "{\"nuevaFechaHora\":\"2026-12-03T12:00\"}");
        assertEquals("application/json; charset=utf-8", ultimaPeticion.get().contentType());
    }

    @Test
    void solicitaCancelacionPorPostSinCuerpo() throws Exception {
        estadoRespuesta = 201;
        ClienteCitasHttp.ResultadoHttp respuesta = cliente.solicitarCancelacion("cita-1");

        assertEquals(201, respuesta.codigoHttp());
        assertTrue(respuesta.esExito());
        assertUltimaPeticion("POST", "/api/citas/cita-1/solicitudes/cancelacion", "");
    }

    @Test
    void consultaSolicitudesPorGetYEnviaToken() throws Exception {
        ClienteCitasHttp.ResultadoHttp respuesta = cliente.consultarSolicitudes();

        assertEquals(200, respuesta.codigoHttp());
        assertTrue(respuesta.esExito());
        assertUltimaPeticion("GET", "/api/solicitudes-cita", "");
    }

    @Test
    void conservaCodigoYCuerpoParaLosErroresHttp() throws Exception {
        int[] codigosError = {400, 401, 403, 404, 405, 409, 500};
        for (int codigo : codigosError) {
            estadoRespuesta = codigo;
            cuerpoRespuesta = "{\"exito\":false,\"codigoError\":\"ERROR_TEST\"}";

            ClienteCitasHttp.ResultadoHttp respuesta = cliente.consultarCitas();

            assertEquals(codigo, respuesta.codigoHttp());
            assertEquals(cuerpoRespuesta, respuesta.cuerpoJson());
            assertFalse(respuesta.esExito());
            assertFalse(respuesta.cuerpoJson().contains(TOKEN));
        }
    }

    @Test
    void comunicaErroresDeConexionSinIncluirElToken() {
        servidorPrueba.stop(0);

        IOException error = assertThrows(IOException.class, () -> cliente.consultarCitas());

        assertFalse(String.valueOf(error.getMessage()).contains(TOKEN));
    }

    @Test
    void rechazaIdentificadoresOCambiosDeFechaInvalidosAntesDeEnviar() {
        assertThrows(IllegalArgumentException.class, () -> cliente.confirmarCita(" "));
        assertThrows(IllegalArgumentException.class, () -> cliente.solicitarCancelacion(""));
        assertThrows(IllegalArgumentException.class,
                () -> cliente.solicitarCambioFecha("cita-1", null));
        assertTrue(ultimaPeticion.get() == null);
    }

    private void responder(HttpExchange intercambio) throws IOException {
        byte[] cuerpoPeticion = intercambio.getRequestBody().readAllBytes();
        ultimaPeticion.set(new PeticionRecibida(
                intercambio.getRequestMethod(),
                intercambio.getRequestURI().getRawPath(),
                intercambio.getRequestHeaders().getFirst("Authorization"),
                intercambio.getRequestHeaders().getFirst("Content-Type"),
                new String(cuerpoPeticion, StandardCharsets.UTF_8)));

        byte[] cuerpo = cuerpoRespuesta.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.sendResponseHeaders(estadoRespuesta, cuerpo.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    private void assertUltimaPeticion(String metodo, String ruta, String cuerpo) {
        PeticionRecibida peticion = ultimaPeticion.get();
        assertNotNull(peticion);
        assertEquals(metodo, peticion.metodo());
        assertEquals(ruta, peticion.ruta());
        assertEquals("Bearer " + TOKEN, peticion.autorizacion());
        assertEquals(cuerpo, peticion.cuerpo());
    }

    private record PeticionRecibida(
            String metodo, String ruta, String autorizacion, String contentType, String cuerpo) {
    }
}
