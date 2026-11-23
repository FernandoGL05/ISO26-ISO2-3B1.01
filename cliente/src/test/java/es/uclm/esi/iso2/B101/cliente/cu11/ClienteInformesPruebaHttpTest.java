package es.uclm.esi.iso2.B101.cliente.cu11;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

class ClienteInformesPruebaHttpTest {
    private static final String TOKEN = "token-medico";
    private HttpServer servidor;
    private ClienteInformesPruebaHttp cliente;
    private final AtomicReference<Peticion> ultimaPeticion = new AtomicReference<>();

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        cliente = new ClienteInformesPruebaHttp(
                "http://127.0.0.1:" + servidor.getAddress().getPort(), TOKEN);
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void creaInformeConJsonSeguroYConsultaSusRutas() throws Exception {
        ClienteInformesPruebaHttp.ResultadoHttp creado =
                cliente.crearInforme("prueba 1", "Hallazgos \"A\"", "Conclusión");

        assertEquals(200, creado.codigoHttp());
        assertEquals("POST", ultimaPeticion.get().metodo());
        assertEquals("/api/informes-prueba", ultimaPeticion.get().ruta());
        assertTrue(ultimaPeticion.get().cuerpo().contains("\"pruebaId\":\"prueba 1\""));
        assertTrue(ultimaPeticion.get().cuerpo().contains("Hallazgos \\\"A\\\""));
        cliente.consultarInformesPaciente();
        assertEquals("GET", ultimaPeticion.get().metodo());
        assertEquals("/api/informes-prueba", ultimaPeticion.get().ruta());
    }

    @Test
    void adjuntaImagenCodificadaYPuedeDescargarla() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        cliente.adjuntarImagen("informe 1", "image/png", png);

        assertEquals("POST", ultimaPeticion.get().metodo());
        assertEquals("/api/informes-prueba/informe%201/imagen", ultimaPeticion.get().ruta());
        assertTrue(ultimaPeticion.get().cuerpo().contains("\"imagenBase64\":"));

        ClienteInformesPruebaHttp.ImagenHttp imagen = cliente.consultarImagen("informe 1");
        assertEquals(200, imagen.codigoHttp());
        assertEquals("image/png", imagen.tipoContenido());
        assertArrayEquals(png, imagen.contenido());
        byte[] copia = imagen.contenido();
        copia[0] = 0;
        assertArrayEquals(png, imagen.contenido());
    }

    @Test
    void rechazaCamposInvalidosAntesDeEnviarPeticion() {
        assertThrows(IllegalArgumentException.class, () -> cliente.crearInforme("", "Hallazgos", "Conclusión"));
        assertThrows(IllegalArgumentException.class,
                () -> cliente.adjuntarImagen("informe-1", "", new byte[] {1}));
        assertNull(ultimaPeticion.get());
    }

    private void responder(HttpExchange intercambio) throws IOException {
        byte[] cuerpo = intercambio.getRequestBody().readAllBytes();
        ultimaPeticion.set(new Peticion(
                intercambio.getRequestMethod(),
                intercambio.getRequestURI().toASCIIString(),
                intercambio.getRequestHeaders().getFirst("Authorization"),
                new String(cuerpo, StandardCharsets.UTF_8)));
        byte[] respuesta = intercambio.getRequestURI().getPath().endsWith("/imagen")
                && intercambio.getRequestMethod().equals("GET")
                ? new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a}
                : "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set(
                "Content-Type",
                intercambio.getRequestMethod().equals("GET") && intercambio.getRequestURI().getPath().endsWith("/imagen")
                        ? "image/png" : "application/json");
        intercambio.sendResponseHeaders(200, respuesta.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(respuesta);
        }
    }

    private record Peticion(String metodo, String ruta, String autorizacion, String cuerpo) {
    }
}
