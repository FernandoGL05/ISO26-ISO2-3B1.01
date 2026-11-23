package es.uclm.esi.iso2.B101.cliente.cu12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteRealizacionPruebasHttpTest {
    private static final String TOKEN = "token-medico";
    private HttpServer servidor;
    private ClienteRealizacionPruebasHttp cliente;
    private final AtomicReference<Peticion> ultimaPeticion = new AtomicReference<>();

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        cliente = new ClienteRealizacionPruebasHttp(
                "http://127.0.0.1:" + servidor.getAddress().getPort(), TOKEN);
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void registraRealizacionYEnviaSesionMedica() throws Exception {
        ClienteRealizacionPruebasHttp.ResultadoHttp respuesta = cliente.registrarRealizacion("cita-1");

        assertEquals(201, respuesta.codigoHttp());
        assertTrue(respuesta.esExito());
        assertEquals("POST", ultimaPeticion.get().metodo());
        assertEquals("/api/realizaciones-prueba", ultimaPeticion.get().ruta());
        assertEquals("Bearer " + TOKEN, ultimaPeticion.get().autorizacion());
        assertEquals("{\"citaId\":\"cita-1\",\"resultado\":\"REALIZADA\"}", ultimaPeticion.get().cuerpo());
    }

    @Test
    void registraIncidenciaYEscapaDescripcion() throws Exception {
        cliente.registrarIncidenciaRealizacion("cita-2", "Equipo \"A\" no responde\nrevisar");

        assertTrue(ultimaPeticion.get().cuerpo().contains("\"resultado\":\"INCIDENCIA\""));
        assertTrue(ultimaPeticion.get().cuerpo().contains("Equipo \\\"A\\\" no responde\\nrevisar"));
    }

    @Test
    void rechazaDatosInvalidosAntesDeEnviarLaPeticion() {
        assertThrows(IllegalArgumentException.class, () -> cliente.registrarRealizacion(" "));
        assertThrows(IllegalArgumentException.class,
                () -> cliente.registrarIncidenciaRealizacion("cita-1", ""));
        assertNull(ultimaPeticion.get());
    }

    private void responder(HttpExchange intercambio) throws IOException {
        byte[] cuerpo = intercambio.getRequestBody().readAllBytes();
        ultimaPeticion.set(new Peticion(
                intercambio.getRequestMethod(),
                intercambio.getRequestURI().toASCIIString(),
                intercambio.getRequestHeaders().getFirst("Authorization"),
                new String(cuerpo, StandardCharsets.UTF_8)));
        byte[] respuesta = "{}".getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.sendResponseHeaders(201, respuesta.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(respuesta);
        }
    }

    private record Peticion(String metodo, String ruta, String autorizacion, String cuerpo) {
    }
}
