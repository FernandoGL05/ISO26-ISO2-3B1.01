package es.uclm.esi.iso2.B101.cliente.cu09;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteSeguimientoPeriodicoHttpTest {
    private static final String TOKEN = "token-medico-temporal";

    private HttpServer servidor;
    private AtomicReference<String> metodo;
    private AtomicReference<String> ruta;
    private AtomicReference<String> autorizacion;
    private AtomicReference<String> cuerpo;
    private AtomicInteger estado;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        metodo = new AtomicReference<>();
        ruta = new AtomicReference<>();
        autorizacion = new AtomicReference<>();
        cuerpo = new AtomicReference<>();
        estado = new AtomicInteger(200);
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/prescripciones-seguimiento", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getRawPath());
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int codigo = estado.get();
            String respuesta = "{\"exito\":true}";
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            intercambio.sendResponseHeaders(codigo, bytes.length);
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
    void registraConsultaDetalleYNecesidadesConBearerYJsonValido() throws Exception {
        ClienteSeguimientoPeriodicoHttp cliente = nuevoCliente();
        ClienteSeguimientoPeriodicoHttp.DatosPrescripcion datos =
                new ClienteSeguimientoPeriodicoHttp.DatosPrescripcion(
                        "paciente-1",
                        "RM \"contraste\"",
                        "Control de lesión",
                        3,
                        LocalDate.of(2028, 2, 29),
                        "PREFERENTE");

        estado.set(201);
        ClienteSeguimientoPeriodicoHttp.ResultadoHttp creada = cliente.registrarPrescripcion(datos);
        assertEquals(201, creada.codigoHttp());
        assertEquals("POST", metodo.get());
        assertEquals("/api/prescripciones-seguimiento", ruta.get());
        assertEquals("Bearer " + TOKEN, autorizacion.get());
        assertTrue(cuerpo.get().contains("\"periodicidadMeses\":3"));
        assertTrue(cuerpo.get().contains("\"tipoPrueba\":\"RM \\\"contraste\\\"\""));

        estado.set(200);
        assertTrue(cliente.consultarPrescripciones().esExito());
        assertEquals("GET", metodo.get());
        assertEquals("/api/prescripciones-seguimiento", ruta.get());
        assertTrue(cliente.consultarDetalle("seguimiento +/á").esExito());
        assertEquals("/api/prescripciones-seguimiento/seguimiento%20%2B%2F%C3%A1", ruta.get());
        assertTrue(cliente.consultarNecesidadesCita("id-1").esExito());
        assertEquals("/api/prescripciones-seguimiento/id-1/necesidades-cita", ruta.get());
    }

    @Test
    void conservaErroresHttpYNoLosConvierteEnExito() throws Exception {
        ClienteSeguimientoPeriodicoHttp cliente = nuevoCliente();
        for (int codigo : new int[] {400, 401, 403, 404, 405, 409}) {
            estado.set(codigo);
            ClienteSeguimientoPeriodicoHttp.ResultadoHttp respuesta = cliente.consultarPrescripciones();
            assertEquals(codigo, respuesta.codigoHttp());
            assertFalse(respuesta.esExito());
        }
    }

    @Test
    void validaEntradaYNoExponeTokenCuandoFallaLaConexion() {
        assertThrows(IllegalArgumentException.class, () -> new ClienteSeguimientoPeriodicoHttp(null, TOKEN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClienteSeguimientoPeriodicoHttp("ftp://localhost", TOKEN));
        assertThrows(IllegalArgumentException.class, () -> new ClienteSeguimientoPeriodicoHttp(urlBase(), " "));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClienteSeguimientoPeriodicoHttp.DatosPrescripcion(
                        "p", "TAC", "Control", 0, LocalDate.now(), "ORDINARIA"));
        ClienteSeguimientoPeriodicoHttp cliente = nuevoCliente();
        assertThrows(IllegalArgumentException.class, () -> cliente.consultarDetalle(" "));

        servidor.stop(0);
        servidor = null;
        IOException error = assertThrows(IOException.class, cliente::consultarPrescripciones);
        assertFalse(error.getMessage().contains(TOKEN));
    }

    private ClienteSeguimientoPeriodicoHttp nuevoCliente() {
        return new ClienteSeguimientoPeriodicoHttp(urlBase(), TOKEN);
    }

    private String urlBase() {
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort()).toString();
    }
}
