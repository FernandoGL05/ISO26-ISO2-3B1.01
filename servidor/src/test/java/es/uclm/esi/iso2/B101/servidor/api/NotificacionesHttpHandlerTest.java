package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.Notificacion;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNotificacionesMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioNotificaciones;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificacionesHttpHandlerTest {
    private HttpServer servidor;
    private HttpClient cliente;
    private ServicioNotificaciones servicio;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        servicio = new ServicioNotificaciones(new RepositorioNotificacionesMemoria());
        AutorizadorNotificaciones autorizador = cabecera -> {
            if ("Bearer token-paciente".equals(cabecera)) {
                return Optional.of(new AutorizadorNotificaciones.IdentidadPaciente(true, "paciente-1"));
            }
            if ("Bearer token-otro".equals(cabecera)) {
                return Optional.of(new AutorizadorNotificaciones.IdentidadPaciente(true, "paciente-2"));
            }
            if ("Bearer token-no-paciente".equals(cabecera)) {
                return Optional.of(new AutorizadorNotificaciones.IdentidadPaciente(false, "usuario-1"));
            }
            return Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        NotificacionesHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        cliente = HttpClient.newHttpClient();
    }

    @AfterEach
    void detenerServidorTemporal() {
        servidor.stop(0);
    }

    @Test
    void consultaNotificacionesPropiasYSerializaCaracteresEspeciales() throws Exception {
        servicio.registrarPreparacionPrueba("paciente-1", "prueba-1", "Preparación: \"rápida\" \\ línea\nsegunda");
        servicio.registrarInformeDisponible("paciente-2", "informe-ajeno");

        HttpResponse<String> respuesta = enviar("GET", "/api/notificaciones", "Bearer token-paciente");

        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(respuesta.body().startsWith("["));
        assertTrue(respuesta.body().contains("Preparación: \\\"rápida\\\" \\\\ línea\\nsegunda"));
        assertFalse(respuesta.body().contains("informe-ajeno"));
    }

    @Test
    void devuelveArrayVacioCuandoNoHayNotificaciones() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/api/notificaciones", "Bearer token-paciente");

        assertEquals(200, respuesta.statusCode());
        assertEquals("[]", respuesta.body());
    }

    @Test
    void marcaComoLeidaUnaNotificacionDelPaciente() throws Exception {
        Notificacion notificacion =
                servicio.registrarInformeDisponible("paciente-1", "informe-1");

        HttpResponse<String> respuesta = enviar(
                "POST", "/api/notificaciones/" + notificacion.getId() + "/leida", "Bearer token-paciente");

        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"leida\":true"));
        assertTrue(notificacion.estaLeida());
    }

    @Test
    void rechazaLeerUnaNotificacionAjenaSinRevelarSuExistencia() throws Exception {
        Notificacion ajena =
                servicio.registrarInformeDisponible("paciente-2", "informe-2");

        HttpResponse<String> respuesta = enviar(
                "POST", "/api/notificaciones/" + ajena.getId() + "/leida", "Bearer token-paciente");

        assertEquals(404, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"exito\":false"));
        assertFalse(ajena.estaLeida());
    }

    @Test
    void rechazaPeticionesSinTokenOTokenInvalido() throws Exception {
        HttpResponse<String> sinToken = enviar("GET", "/api/notificaciones", null);
        HttpResponse<String> tokenInvalido = enviar("GET", "/api/notificaciones", "Bearer invalido");

        assertEquals(401, sinToken.statusCode());
        assertEquals(401, tokenInvalido.statusCode());
        assertTrue(sinToken.body().contains("\"codigoError\":\"NO_AUTENTICADO\""));
    }

    @Test
    void rechazaUsuariosSinRolPaciente() throws Exception {
        HttpResponse<String> respuesta =
                enviar("GET", "/api/notificaciones", "Bearer token-no-paciente");

        assertEquals(403, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"codigoError\":\"PROHIBIDO\""));
    }

    @Test
    void rechazaMetodosIncorrectosYRutasQueSoloCoincidenPorPrefijo() throws Exception {
        HttpResponse<String> metodoIncorrecto =
                enviar("POST", "/api/notificaciones", "Bearer token-paciente");
        HttpResponse<String> rutaIncorrecta =
                enviar("GET", "/api/notificaciones-extra", "Bearer token-paciente");

        assertEquals(405, metodoIncorrecto.statusCode());
        assertTrue(metodoIncorrecto.headers().firstValue("Allow").orElse("").contains("GET"));
        assertEquals(404, rutaIncorrecta.statusCode());
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String autorizacion) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create(urlBase() + ruta))
                .header("Accept", "application/json")
                .method(
                        metodo,
                        metodo.equals("GET")
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString("", StandardCharsets.UTF_8));
        if (autorizacion != null) {
            solicitud.header("Authorization", autorizacion);
        }
        return cliente.send(solicitud.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String urlBase() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }
}
