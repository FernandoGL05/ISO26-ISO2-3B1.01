package es.uclm.esi.iso2.B101.cliente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.cliente.comunicacion.ClienteAutenticacion;
import es.uclm.esi.iso2.B101.cliente.cu02.ClienteCitasHttp;
import es.uclm.esi.iso2.B101.cliente.cu08.ClientePrescripcionesHttp;
import es.uclm.esi.iso2.B101.cliente.cu08.ClientePrescripcionesHttp.DatosPrescripcion;
import es.uclm.esi.iso2.B101.servidor.api.AplicacionServidor;
import es.uclm.esi.iso2.B101.servidor.api.ServidorHTTP;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClienteAutenticacionCasosUsoIntegracionTest {
    private ServidorHTTP servidor;

    @AfterEach
    void detenerServidor() {
        if (servidor != null) {
            servidor.detener();
        }
    }

    @Test
    void reutilizaElTokenDeCu01ParaAccederACu02YCu08() throws IOException, InterruptedException {
        servidor = AplicacionServidor.crearServidor(0);
        servidor.iniciar();
        String baseUrl = "http://127.0.0.1:" + servidor.getPuerto();
        ClienteAutenticacion autenticacion = new ClienteAutenticacion(baseUrl);

        var respuestaPaciente = autenticacion.iniciarSesion("paciente1", "paciente123");
        assertTrue(respuestaPaciente.exito());
        String tokenPaciente = respuestaPaciente.obtenerSesion().orElseThrow().token();
        var citas = new ClienteCitasHttp(baseUrl, tokenPaciente).consultarCitas();
        assertEquals(200, citas.codigoHttp());

        var respuestaMedico = autenticacion.iniciarSesion("medico1", "medico123");
        assertTrue(respuestaMedico.exito());
        String tokenMedico = respuestaMedico.obtenerSesion().orElseThrow().token();
        ClientePrescripcionesHttp clientePrescripciones = new ClientePrescripcionesHttp(baseUrl, tokenMedico);
        var creada = clientePrescripciones.prescribir(new DatosPrescripcion(
                "paciente-1", "TAC", "Dolor", "Descartar lesión", "URGENTE"));
        assertEquals(201, creada.codigoHttp());
        var prescripciones = clientePrescripciones.consultarSolicitudes();
        assertEquals(200, prescripciones.codigoHttp());
        assertTrue(prescripciones.cuerpoJson().contains("\"medicoId\":\"medico-1\""));
    }
}
