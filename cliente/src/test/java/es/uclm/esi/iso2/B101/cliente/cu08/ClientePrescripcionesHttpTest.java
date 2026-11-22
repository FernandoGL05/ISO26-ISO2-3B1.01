package es.uclm.esi.iso2.B101.cliente.cu08;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientePrescripcionesHttpTest {
    private static final String TOKEN = "token-medico-de-prueba";

    private HttpServer servidor;
    private AtomicReference<String> metodo;
    private AtomicReference<String> ruta;
    private AtomicReference<String> autorizacion;
    private AtomicReference<String> cuerpo;
    private AtomicInteger estadoRespuesta;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        metodo = new AtomicReference<>();
        ruta = new AtomicReference<>();
        autorizacion = new AtomicReference<>();
        cuerpo = new AtomicReference<>();
        estadoRespuesta = new AtomicInteger(200);
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/prescripciones-pruebas", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getRawPath());
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int estado = estadoRespuesta.get();
            String respuesta = estado == 200 || estado == 201
                    ? "[]"
                    : "{\"exito\":false,\"codigoError\":\"ERROR\"}";
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            intercambio.sendResponseHeaders(estado, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void creaConsultaListaYConsultaDetalleConTokenBearer() throws Exception {
        ClientePrescripcionesHttp cliente = nuevoCliente();
        ClientePrescripcionesHttp.DatosPrescripcion datos = new ClientePrescripcionesHttp.DatosPrescripcion(
                "paciente-1", "TAC \"especial\"", "Dolor", "Descartar lesión", "URGENTE");

        estadoRespuesta.set(201);
        ClientePrescripcionesHttp.ResultadoHttp creada = cliente.prescribir(datos);
        assertEquals(201, creada.codigoHttp());
        assertEquals("POST", metodo.get());
        assertEquals("/api/prescripciones-pruebas", ruta.get());
        assertEquals("Bearer " + TOKEN, autorizacion.get());
        assertTrue(cuerpo.get().contains("\"tipoPrueba\":\"TAC \\\"especial\\\"\""));
        assertTrue(cuerpo.get().contains("\"prioridad\":\"URGENTE\""));

        ClientePrescripcionesHttp.ResultadoHttp lista = cliente.consultarSolicitudes();
        assertEquals("GET", metodo.get());
        assertEquals("/api/prescripciones-pruebas", ruta.get());
        assertTrue(lista.esExito());

        ClientePrescripcionesHttp.ResultadoHttp detalle = cliente.consultarDetalle("solicitud +1");
        assertEquals("GET", metodo.get());
        assertEquals("/api/prescripciones-pruebas/solicitud%20%2B1", ruta.get());
        assertTrue(detalle.esExito());
    }

    @Test
    void conservaErroresHttpComoResultadosNoExitosos() throws Exception {
        ClientePrescripcionesHttp cliente = nuevoCliente();

        for (int estado : new int[] {400, 401, 403, 404, 405, 409}) {
            estadoRespuesta.set(estado);
            ClientePrescripcionesHttp.ResultadoHttp respuesta = cliente.consultarSolicitudes();
            assertEquals(estado, respuesta.codigoHttp());
            assertFalse(respuesta.esExito());
            assertTrue(respuesta.cuerpoJson().contains("\"codigoError\":\"ERROR\""));
        }
    }

    @Test
    void validaDatosUrlTokenYIdYNoFiltraElTokenEnErroresDeConexion() {
        assertThrows(IllegalArgumentException.class, () -> nuevoCliente(null, TOKEN));
        assertThrows(IllegalArgumentException.class, () -> nuevoCliente("ftp://localhost", TOKEN));
        assertThrows(IllegalArgumentException.class, () -> nuevoCliente(urlBase(), " "));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClientePrescripcionesHttp.DatosPrescripcion("p", "TAC", "I", "J", "URGENTE-PLUS"));

        ClientePrescripcionesHttp cliente = nuevoCliente();
        assertThrows(IllegalArgumentException.class, () -> cliente.consultarDetalle(" "));
        servidor.stop(0);
        servidor = null;
        IOException error = assertThrows(IOException.class, cliente::consultarSolicitudes);
        assertFalse(error.getMessage().contains(TOKEN));
    }

    private ClientePrescripcionesHttp nuevoCliente() {
        return nuevoCliente(urlBase(), TOKEN);
    }

    private ClientePrescripcionesHttp nuevoCliente(String base, String token) {
        return new ClientePrescripcionesHttp(base, token);
    }

    private String urlBase() {
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort()).toString();
    }
}
