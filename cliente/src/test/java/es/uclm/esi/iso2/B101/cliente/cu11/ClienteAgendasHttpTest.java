package es.uclm.esi.iso2.B101.cliente.cu11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import es.uclm.esi.iso2.B101.cliente.cu11.ClienteAgendasHttp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteAgendasHttpTest {
    private static final String TOKEN = "token-de-prueba";
    private HttpServer servidor;
    private ClienteAgendasHttp cliente;
    private final AtomicReference<HttpRequestRecibida> ultimaPeticion = new AtomicReference<>();

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        String urlBase = "http://127.0.0.1:" + servidor.getAddress().getPort();
        cliente = new ClienteAgendasHttp(urlBase, TOKEN);
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void consultaDisponibilidadCodificandoLosParametrosYEnviandoElToken() throws Exception {
        ClienteAgendasHttp.ResultadoHttp respuesta = cliente.consultarDisponibilidad(
                "RM con contraste",
                LocalDateTime.of(2026, 12, 1, 9, 0),
                LocalDateTime.of(2026, 12, 1, 12, 0),
                30);

        assertEquals(200, respuesta.codigoHttp());
        assertTrue(respuesta.esExito());
        assertEquals("GET", ultimaPeticion.get().metodo());
        assertTrue(ultimaPeticion.get().ruta().startsWith("/api/agendas/disponibilidad?"));
        assertTrue(ultimaPeticion.get().ruta().contains("tipoPrueba=RM+con+contraste"));
        assertEquals("Bearer " + TOKEN, ultimaPeticion.get().autorizacion());
    }

    @Test
    void rechazaConsultaInvalidaAntesDeEnviarLaPeticion() {
        assertThrows(IllegalArgumentException.class,
                () -> cliente.consultarDisponibilidad(
                        "", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 30));
        assertThrows(IllegalArgumentException.class,
                () -> cliente.consultarDisponibilidad(
                        "RM", LocalDateTime.of(2026, 12, 1, 9, 0),
                        LocalDateTime.of(2026, 12, 2, 9, 0), 30));

        assertNull(ultimaPeticion.get());
    }

    private void responder(HttpExchange intercambio) throws IOException {
        ultimaPeticion.set(new HttpRequestRecibida(
                intercambio.getRequestMethod(),
                intercambio.getRequestURI().toASCIIString(),
                intercambio.getRequestHeaders().getFirst("Authorization")));
        byte[] respuesta = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.sendResponseHeaders(200, respuesta.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(respuesta);
        }
    }

    private record HttpRequestRecibida(String metodo, String ruta, String autorizacion) {
    }
}
