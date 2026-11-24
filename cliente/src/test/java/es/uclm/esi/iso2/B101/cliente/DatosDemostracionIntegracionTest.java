package es.uclm.esi.iso2.B101.cliente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.cliente.comunicacion.ClienteAutenticacion;
import es.uclm.esi.iso2.B101.cliente.comunicacion.JsonClienteUtil;
import es.uclm.esi.iso2.B101.cliente.cu02.ClienteCitasHttp;
import es.uclm.esi.iso2.B101.cliente.cu03.ClienteNotificacionesHttp;
import es.uclm.esi.iso2.B101.cliente.cu04.ClienteHistorialPruebasHttp;
import es.uclm.esi.iso2.B101.cliente.cu05.ClienteIncidenciasHttp;
import es.uclm.esi.iso2.B101.cliente.cu06.ClienteEncuestasHttp;
import es.uclm.esi.iso2.B101.cliente.cu07.ClienteSeguimientosHttp;
import es.uclm.esi.iso2.B101.cliente.cu11.ClienteInformesPruebaHttp;
import es.uclm.esi.iso2.B101.cliente.cu12.ClienteRealizacionPruebasHttp;
import es.uclm.esi.iso2.B101.servidor.api.AplicacionServidor;
import es.uclm.esi.iso2.B101.servidor.api.ServidorHTTP;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DatosDemostracionIntegracionTest {
    private ServidorHTTP servidor;
    private String baseUrl;
    private ClienteAutenticacion autenticacion;

    @AfterEach
    void detenerServidor() {
        if (servidor != null) {
            servidor.detener();
        }
    }

    @Test
    void datosDemostracionSonCoherentesYPermitenCompletarCu12YC11() throws Exception {
        iniciarServidor(true);
        String tokenPaciente = iniciarSesion("paciente1", "paciente123");
        String tokenMedico = iniciarSesion("medico1", "medico123");

        var citas = new ClienteCitasHttp(baseUrl, tokenPaciente).consultarCitas();
        assertEquals(200, citas.codigoHttp());
        assertTrue(citas.cuerpoJson().contains("\"id\":\"DEMO-CITA-CU12\""));
        assertTrue(citas.cuerpoJson().contains("\"id\":\"DEMO-CITA-INCIDENCIA\""));
        assertTrue(citas.cuerpoJson().contains("\"id\":\"DEMO-CITA-REALIZADA\""));
        assertTrue(citas.cuerpoJson().contains(LocalDate.now().plusDays(2).toString()));

        var notificaciones = new ClienteNotificacionesHttp(baseUrl, tokenPaciente).consultarNotificaciones();
        assertEquals(200, notificaciones.codigoHttp());
        assertTrue(notificaciones.cuerpoJson().contains("\"referenciaId\":\"DEMO-CITA-CU12\""));

        var historial = new ClienteHistorialPruebasHttp(baseUrl, tokenPaciente).consultarHistorial();
        assertEquals(200, historial.codigoHttp());
        assertTrue(historial.cuerpoJson().contains("\"id\":\"DEMO-PRUEBA-REALIZADA\""));
        assertTrue(historial.cuerpoJson().contains("\"citaId\":\"DEMO-CITA-REALIZADA\""));
        assertTrue(historial.cuerpoJson().contains(LocalDate.now().minusDays(2).toString()));

        var incidencias = new ClienteIncidenciasHttp(baseUrl, tokenPaciente).consultarIncidencias();
        assertEquals(200, incidencias.codigoHttp());
        assertTrue(incidencias.cuerpoJson().contains("\"id\":\"DEMO-INCIDENCIA-CONSULTA\""));
        assertTrue(incidencias.cuerpoJson().contains("\"pacienteId\":\"paciente-1\""));

        var encuestas = new ClienteEncuestasHttp(baseUrl, tokenPaciente).consultarEncuestas();
        assertEquals(200, encuestas.codigoHttp());
        assertTrue(encuestas.cuerpoJson().contains("\"id\":\"DEMO-ENCUESTA-SATISFACCION\""));

        var seguimientos = new ClienteSeguimientosHttp(baseUrl, tokenPaciente).consultarSeguimientos();
        assertEquals(200, seguimientos.codigoHttp());
        assertTrue(seguimientos.cuerpoJson().contains("\"id\":\"DEMO-SEGUIMIENTO-TAC\""));

        ClienteRealizacionPruebasHttp realizacion = new ClienteRealizacionPruebasHttp(baseUrl, tokenMedico);
        var respuestaRealizacion = realizacion.registrarRealizacion("DEMO-CITA-CU12");
        assertEquals(201, respuestaRealizacion.codigoHttp());
        Map<String, String> prueba = JsonClienteUtil.parsearObjetoPlano(respuestaRealizacion.cuerpoJson());
        String pruebaId = prueba.get("id");
        assertTrue(pruebaId != null && !pruebaId.isBlank());

        var historialActualizado = new ClienteHistorialPruebasHttp(baseUrl, tokenPaciente).consultarHistorial();
        assertTrue(historialActualizado.cuerpoJson().contains("\"citaId\":\"DEMO-CITA-CU12\""));
        assertTrue(historialActualizado.cuerpoJson().contains("\"id\":\"" + pruebaId + "\""));

        var informe = new ClienteInformesPruebaHttp(baseUrl, tokenMedico)
                .crearInforme(pruebaId, "Hallazgos de demostración", "Informe E2E de demostración");
        assertEquals(201, informe.codigoHttp());
        assertTrue(informe.cuerpo().contains("\"pacienteId\":\"paciente-1\""));
        assertTrue(informe.cuerpo().contains("\"medicoId\":\"medico-1\""));

        var informePaciente =
                new ClienteInformesPruebaHttp(baseUrl, tokenPaciente).consultarInformesPaciente();
        assertEquals(200, informePaciente.codigoHttp());
        assertTrue(informePaciente.cuerpo().contains("\"pruebaId\":\"" + pruebaId + "\""));

        var incidenciaNueva =
                realizacion.registrarIncidenciaRealizacion("DEMO-CITA-INCIDENCIA", "Incidencia de demostración");
        assertEquals(201, incidenciaNueva.codigoHttp());
        var incidenciasActualizadas =
                new ClienteIncidenciasHttp(baseUrl, tokenPaciente).consultarIncidencias();
        assertTrue(incidenciasActualizadas.cuerpoJson().contains("\"citaId\":\"DEMO-CITA-INCIDENCIA\""));
        assertTrue(incidenciasActualizadas.cuerpoJson().contains("\"descripcion\":\"Incidencia de demostración\""));
    }

    @Test
    void laFabricaHabitualNoPrecargaDatosDeDemostracion() throws Exception {
        iniciarServidor(false);
        String tokenPaciente = iniciarSesion("paciente1", "paciente123");

        var citas = new ClienteCitasHttp(baseUrl, tokenPaciente).consultarCitas();
        var historial = new ClienteHistorialPruebasHttp(baseUrl, tokenPaciente).consultarHistorial();
        var incidencias = new ClienteIncidenciasHttp(baseUrl, tokenPaciente).consultarIncidencias();
        var notificaciones = new ClienteNotificacionesHttp(baseUrl, tokenPaciente).consultarNotificaciones();
        var encuestas = new ClienteEncuestasHttp(baseUrl, tokenPaciente).consultarEncuestas();
        var seguimientos = new ClienteSeguimientosHttp(baseUrl, tokenPaciente).consultarSeguimientos();

        assertEquals(200, citas.codigoHttp());
        assertEquals(200, historial.codigoHttp());
        assertEquals(200, incidencias.codigoHttp());
        assertEquals(200, notificaciones.codigoHttp());
        assertEquals(200, encuestas.codigoHttp());
        assertEquals(200, seguimientos.codigoHttp());
        assertEquals("[]", citas.cuerpoJson());
        assertFalse(citas.cuerpoJson().contains("DEMO-"));
        assertFalse(historial.cuerpoJson().contains("DEMO-"));
        assertFalse(incidencias.cuerpoJson().contains("DEMO-"));
        assertFalse(notificaciones.cuerpoJson().contains("DEMO-"));
        assertFalse(encuestas.cuerpoJson().contains("DEMO-"));
        assertFalse(seguimientos.cuerpoJson().contains("DEMO-"));
    }

    private void iniciarServidor(boolean modoDemostracion) throws IOException {
        servidor = modoDemostracion
                ? AplicacionServidor.crearServidor(0, true)
                : AplicacionServidor.crearServidor(0);
        servidor.iniciar();
        baseUrl = "http://127.0.0.1:" + servidor.getPuerto();
        autenticacion = new ClienteAutenticacion(baseUrl);
    }

    private String iniciarSesion(String usuario, String contrasena) {
        var respuesta = autenticacion.iniciarSesion(usuario, contrasena);
        assertTrue(respuesta.exito(), respuesta.mensaje());
        return respuesta.obtenerSesion().orElseThrow().token();
    }
}
