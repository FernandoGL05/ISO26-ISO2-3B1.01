package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioInformesPruebaMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioInformesPrueba;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InformesPruebaHttpHandlerTest {
    private static final String RUTA = "/api/informes-prueba";
    private static final String TOKEN_MEDICO = "medico-1";
    private static final String TOKEN_PACIENTE = "paciente-1";
    private static final String TOKEN_AJENO = "paciente-2";
    private static final String TOKEN_OTRO_ROL = "otro";
    private HttpServer servidor;
    private URI baseUri;
    private HttpClient cliente;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        RepositorioHistorialPruebasMemoria pruebas = new RepositorioHistorialPruebasMemoria();
        pruebas.registrar(new PruebaRealizada(
                "prueba-1", "paciente-1", "RM", LocalDateTime.of(2026, 11, 20, 10, 0), null));
        ServicioInformesPrueba servicio =
                new ServicioInformesPrueba(pruebas, new RepositorioInformesPruebaMemoria());
        AutorizadorInformesPrueba autorizador = cabecera -> switch (cabecera) {
            case "Bearer " + TOKEN_MEDICO -> Optional.of(
                    new AutorizadorInformesPrueba.Identidad(false, null, true, "medico-1"));
            case "Bearer " + TOKEN_PACIENTE -> Optional.of(
                    new AutorizadorInformesPrueba.Identidad(true, "paciente-1", false, null));
            case "Bearer " + TOKEN_AJENO -> Optional.of(
                    new AutorizadorInformesPrueba.Identidad(true, "paciente-2", false, null));
            case "Bearer " + TOKEN_OTRO_ROL -> Optional.of(
                    new AutorizadorInformesPrueba.Identidad(false, null, false, null));
            default -> Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        InformesPruebaHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        cliente = HttpClient.newHttpClient();
        baseUri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void creaInformeYPermiteConsultarloSoloAlPacientePropietarioOMedicoAutor() throws Exception {
        HttpResponse<String> creado = enviar(
                "POST", RUTA, TOKEN_MEDICO, "application/json",
                "{\"pruebaId\":\"prueba-1\",\"hallazgos\":\"Hallazgos\",\"conclusion\":\"Conclusión\"}");

        assertEquals(201, creado.statusCode());
        assertTrue(creado.body().contains("\"fechaCreacion\":"));
        assertTrue(creado.body().contains("\"tieneImagen\":false"));
        String id = extraer(creado.body(), "id");
        assertEquals(200, enviar("GET", RUTA + "/" + id, TOKEN_PACIENTE, null, null).statusCode());
        assertEquals(200, enviar("GET", RUTA + "/" + id, TOKEN_MEDICO, null, null).statusCode());
        assertEquals(404, enviar("GET", RUTA + "/" + id, TOKEN_AJENO, null, null).statusCode());
        assertEquals(200, enviar("GET", RUTA, TOKEN_PACIENTE, null, null).statusCode());
        assertEquals(200, enviar("GET", RUTA + "/medico", TOKEN_MEDICO, null, null).statusCode());
    }

    @Test
    void permiteAdjuntarYConsultarImagenConAccesoAutorizado() throws Exception {
        String informe = crearInforme();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        String cuerpo = "{\"tipoImagen\":\"image/png\",\"imagenBase64\":\""
                + Base64.getEncoder().encodeToString(png) + "\"}";

        HttpResponse<String> adjuntada = enviar(
                "POST", RUTA + "/" + informe + "/imagen", TOKEN_MEDICO, "application/json", cuerpo);
        HttpResponse<String> imagen = enviar("GET", RUTA + "/" + informe + "/imagen", TOKEN_PACIENTE, null, null);

        assertEquals(200, adjuntada.statusCode());
        assertTrue(adjuntada.body().contains("\"tieneImagen\":true"));
        assertEquals(200, imagen.statusCode());
        assertEquals("image/png", imagen.headers().firstValue("Content-Type").orElse(""));
        assertEquals(png.length, imagen.body().getBytes(StandardCharsets.ISO_8859_1).length);
        assertEquals(404, enviar("GET", RUTA + "/" + informe + "/imagen", TOKEN_AJENO, null, null).statusCode());
    }

    @Test
    void exigeAutenticacionRolesYFormatoValido() throws Exception {
        assertEquals(401, enviar("GET", RUTA, null, null, null).statusCode());
        assertEquals(403, enviar(
                "POST", RUTA, TOKEN_PACIENTE, "application/json",
                "{\"pruebaId\":\"prueba-1\",\"hallazgos\":\"H\",\"conclusion\":\"C\"}").statusCode());
        assertEquals(403, enviar("GET", RUTA, TOKEN_OTRO_ROL, null, null).statusCode());
        assertEquals(400, enviar("POST", RUTA, TOKEN_MEDICO, "application/json", "{").statusCode());
        assertEquals(404, enviar("GET", RUTA + "-inexistente", TOKEN_PACIENTE, null, null).statusCode());
        assertFalse(enviar("GET", RUTA, TOKEN_PACIENTE, null, null).body().contains("medico-1"));
    }

    private String crearInforme() throws Exception {
        HttpResponse<String> creado = enviar(
                "POST", RUTA, TOKEN_MEDICO, "application/json",
                "{\"pruebaId\":\"prueba-1\",\"hallazgos\":\"H\",\"conclusion\":\"C\"}");
        assertEquals(201, creado.statusCode());
        return extraer(creado.body(), "id");
    }

    private String extraer(String json, String clave) {
        String prefijo = "\"" + clave + "\":\"";
        int inicio = json.indexOf(prefijo) + prefijo.length();
        return json.substring(inicio, json.indexOf('"', inicio));
    }

    private HttpResponse<String> enviar(
            String metodo, String ruta, String token, String tipoContenido, String cuerpo) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(baseUri.resolve(ruta))
                .header("Accept", "application/json");
        if (token != null) {
            solicitud.header("Authorization", "Bearer " + token);
        }
        if (tipoContenido != null) {
            solicitud.header("Content-Type", tipoContenido);
        }
        HttpRequest.BodyPublisher contenido = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8);
        return cliente.send(
                solicitud.method(metodo, contenido).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.ISO_8859_1));
    }
}
