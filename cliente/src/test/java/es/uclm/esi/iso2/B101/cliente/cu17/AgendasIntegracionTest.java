package es.uclm.esi.iso2.B101.cliente.cu17;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.api.AgendasHttpHandler;
import es.uclm.esi.iso2.B101.servidor.api.AutorizadorAgendas;
import es.uclm.esi.iso2.B101.servidor.dominio.AgendaDiaria;
import es.uclm.esi.iso2.B101.servidor.dominio.HuecoAgenda;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAgendasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHuecosAgendaMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAgendas;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgendasIntegracionTest {
    private HttpServer servidor;
    private ServicioAgendas servicio;
    private ClienteAgendasHttp cliente;
    private String urlBase;

    @BeforeEach
    void preparar() throws Exception {
        servicio = new ServicioAgendas(
                new RepositorioAgendasMemoria(),
                new RepositorioHuecosAgendaMemoria());

        servidor = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        AgendasHttpHandler.registrarEn(
                servidor, servicio, cabecera -> {
                    if ("Bearer admision-test".equals(cabecera)) {
                        return Optional.of(
                                new AutorizadorAgendas.IdentidadAutorizada(
                                        "admision-1", true));
                    }
                    return Optional.empty();
                });

        servidor.start();

        urlBase = "http://127.0.0.1:"
                + servidor.getAddress().getPort();

        cliente = new ClienteAgendasHttp(urlBase, "admision-test");
    }

    @AfterEach
    void cerrar() {
        if (cliente != null) {
            cliente.close();
        }
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void completaElFlujoDeAgendaYHuecosPorHttp() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 12, 3);

        var creacion = cliente.crearAgenda("medico-1", fecha);
        assertEquals(201, creacion.codigoHttp());

        AgendaDiaria agenda =
                servicio.consultarAgendaPorMedicoYFecha(
                        "medico-1", fecha);

        var consulta = cliente.consultarAgenda(agenda.getId());
        assertEquals(200, consulta.codigoHttp());
        assertEquals(creacion.cuerpoJson(), consulta.cuerpoJson());

        var listado = cliente.consultarAgendasPorFecha(fecha);
        assertEquals(200, listado.codigoHttp());
        assertEquals(
                "[" + creacion.cuerpoJson() + "]",
                listado.cuerpoJson());

        var filtro = cliente.consultarAgendaPorMedicoYFecha(
                "medico-1", fecha);
        assertEquals(200, filtro.codigoHttp());
        assertEquals(creacion.cuerpoJson(), filtro.cuerpoJson());

        var altaHueco = cliente.agregarHueco(
                agenda.getId(),
                LocalTime.of(9, 0),
                LocalTime.of(9, 30));

        assertEquals(201, altaHueco.codigoHttp());

        HuecoAgenda hueco =
                servicio.consultarHuecos(agenda.getId()).get(0);

        var solapamiento = cliente.agregarHueco(
                agenda.getId(),
                LocalTime.of(9, 15),
                LocalTime.of(9, 45));

        assertEquals(409, solapamiento.codigoHttp());
        assertEquals(1,
                servicio.consultarHuecos(agenda.getId()).size());

        var cambio = cliente.modificarHueco(
                agenda.getId(),
                hueco.getId(),
                LocalTime.of(10, 0),
                LocalTime.of(10, 30));

        assertEquals(200, cambio.codigoHttp());
        assertEquals(
                LocalTime.of(10, 0),
                servicio.consultarHuecos(agenda.getId())
                        .get(0).getHoraInicio());

        var huecos = cliente.consultarHuecos(agenda.getId());
        assertEquals(200, huecos.codigoHttp());
        assertEquals(
                "[" + cambio.cuerpoJson() + "]",
                huecos.cuerpoJson());

        var borrado = cliente.eliminarHueco(
                agenda.getId(), hueco.getId());

        assertEquals(200, borrado.codigoHttp());
        assertTrue(
                servicio.consultarHuecos(agenda.getId()).isEmpty());

        var vacio = cliente.consultarHuecos(agenda.getId());
        assertEquals(200, vacio.codigoHttp());
        assertEquals("[]", vacio.cuerpoJson());
    }

    @Test
    void rechazaTokenInvalidoSinCrearLaAgenda() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 12, 3);

        try (var noAutorizado =
                new ClienteAgendasHttp(urlBase, "token-invalido")) {

            var respuesta =
                    noAutorizado.crearAgenda("medico-1", fecha);

            assertEquals(401, respuesta.codigoHttp());
            assertFalse(respuesta.esExito());
        }

        assertTrue(servicio.consultarAgendasPorFecha(fecha).isEmpty());
    }
}