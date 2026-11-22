package es.uclm.esi.iso2.B101.servidor.servicio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import es.uclm.esi.iso2.B101.servidor.dominio.AgendaDiaria;
import es.uclm.esi.iso2.B101.servidor.dominio.HuecoAgenda;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAgendasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHuecosAgendaMemoria;

class ServicioAgendasTest {
    private ServicioAgendas servicio;
    private static final LocalDate FECHA = LocalDate.of(2026, 12, 3);

    @BeforeEach
    void preparar() {
        servicio = new ServicioAgendas(
                new RepositorioAgendasMemoria(),
                new RepositorioHuecosAgendaMemoria());
    }

    @Test
    void creaYConsultaAgendaPorMedicoYFecha() {
        AgendaDiaria agenda = servicio.crearAgenda("medico-1", FECHA);

        AgendaDiaria encontrada =
                servicio.consultarAgendaPorMedicoYFecha("medico-1", FECHA);

        assertEquals(agenda.getId(), encontrada.getId());
        assertEquals("medico-1", encontrada.getMedicoId());
        assertEquals(FECHA, encontrada.getFecha());
    }

    @Test
    void rechazaDosAgendasDelMismoMedicoElMismoDia() {
        servicio.crearAgenda("medico-1", FECHA);

        assertThrows(IllegalStateException.class,
                () -> servicio.crearAgenda("medico-1", FECHA));
    }

    @Test
    void permiteAgendasDeDistintosMedicosOMismoMedicoEnOtraFecha() {
        servicio.crearAgenda("medico-1", FECHA);
        servicio.crearAgenda("medico-2", FECHA);
        servicio.crearAgenda("medico-1", FECHA.plusDays(1));

        assertEquals(2, servicio.consultarAgendasPorFecha(FECHA).size());
        assertEquals(1,
                servicio.consultarAgendasPorFecha(FECHA.plusDays(1)).size());
    }

    @Test
    void devuelveHuecosOrdenadosYPermiteFranjasContiguas() {
        AgendaDiaria agenda = servicio.crearAgenda("medico-1", FECHA);

        HuecoAgenda segundo = servicio.agregarHueco(
                agenda.getId(), LocalTime.of(9, 30), LocalTime.of(10, 0));
        HuecoAgenda primero = servicio.agregarHueco(
                agenda.getId(), LocalTime.of(9, 0), LocalTime.of(9, 30));

        List<HuecoAgenda> huecos = servicio.consultarHuecos(agenda.getId());

        assertEquals(List.of(primero.getId(), segundo.getId()),
                huecos.stream().map(HuecoAgenda::getId).toList());
    }

    @Test
    void rechazaSolapamientoSinGuardarElNuevoHueco() {
        AgendaDiaria agenda = servicio.crearAgenda("medico-1", FECHA);

        servicio.agregarHueco(
                agenda.getId(), LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThrows(IllegalStateException.class,
                () -> servicio.agregarHueco(
                        agenda.getId(),
                        LocalTime.of(9, 30),
                        LocalTime.of(10, 30)));

        assertEquals(1, servicio.consultarHuecos(agenda.getId()).size());
    }

    @Test
    void permiteLasMismasHorasEnAgendasDistintas() {
        AgendaDiaria primera = servicio.crearAgenda("medico-1", FECHA);
        AgendaDiaria segunda = servicio.crearAgenda("medico-2", FECHA);

        servicio.agregarHueco(
                primera.getId(), LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertDoesNotThrow(() -> servicio.agregarHueco(
                segunda.getId(), LocalTime.of(9, 0), LocalTime.of(10, 0)));
    }

    @Test
    void rechazaIntervalosInvalidos() {
        AgendaDiaria agenda = servicio.crearAgenda("medico-1", FECHA);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.agregarHueco(
                        agenda.getId(), LocalTime.NOON, LocalTime.NOON));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.agregarHueco(
                        agenda.getId(),
                        LocalTime.of(11, 0),
                        LocalTime.of(10, 0)));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.agregarHueco(
                        agenda.getId(), null, LocalTime.NOON));

        assertTrue(servicio.consultarHuecos(agenda.getId()).isEmpty());
    }

    @Test
    void modificaUnHuecoSinDuplicarlo() {
        AgendaDiaria agenda = servicio.crearAgenda("medico-1", FECHA);
        HuecoAgenda hueco = servicio.agregarHueco(
                agenda.getId(), LocalTime.of(9, 0), LocalTime.of(9, 30));

        servicio.modificarHueco(
                agenda.getId(), hueco.getId(),
                LocalTime.of(10, 0), LocalTime.of(10, 30));

        List<HuecoAgenda> encontrados =
                servicio.consultarHuecos(agenda.getId());

        assertEquals(1, encontrados.size());
        assertEquals(hueco.getId(), encontrados.get(0).getId());
        assertEquals(LocalTime.of(10, 0),
                encontrados.get(0).getHoraInicio());
    }

    @Test
    void modificacionSolapadaConservaElHorarioAnterior() {
        AgendaDiaria agenda = servicio.crearAgenda("medico-1", FECHA);

        servicio.agregarHueco(
                agenda.getId(), LocalTime.of(9, 0), LocalTime.of(10, 0));
        HuecoAgenda segundo = servicio.agregarHueco(
                agenda.getId(), LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThrows(IllegalStateException.class,
                () -> servicio.modificarHueco(
                        agenda.getId(), segundo.getId(),
                        LocalTime.of(9, 30), LocalTime.of(10, 30)));

        HuecoAgenda conservado = servicio.consultarHuecos(agenda.getId())
                .stream()
                .filter(h -> h.getId().equals(segundo.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(LocalTime.of(10, 0), conservado.getHoraInicio());
        assertEquals(LocalTime.of(11, 0), conservado.getHoraFin());
    }

    @Test
    void impideModificarOEliminarHuecosDeOtraAgenda() {
        AgendaDiaria primera = servicio.crearAgenda("medico-1", FECHA);
        AgendaDiaria segunda = servicio.crearAgenda("medico-2", FECHA);

        HuecoAgenda hueco = servicio.agregarHueco(
                primera.getId(), LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThrows(NoSuchElementException.class,
                () -> servicio.modificarHueco(
                        segunda.getId(), hueco.getId(),
                        LocalTime.of(11, 0), LocalTime.NOON));

        assertThrows(NoSuchElementException.class,
                () -> servicio.eliminarHueco(
                        segunda.getId(), hueco.getId()));

        assertEquals(1, servicio.consultarHuecos(primera.getId()).size());
    }

    @Test
    void eliminaUnHuecoExistente() {
        AgendaDiaria agenda = servicio.crearAgenda("medico-1", FECHA);
        HuecoAgenda hueco = servicio.agregarHueco(
                agenda.getId(), LocalTime.of(9, 0), LocalTime.of(10, 0));

        servicio.eliminarHueco(agenda.getId(), hueco.getId());

        assertTrue(servicio.consultarHuecos(agenda.getId()).isEmpty());
        assertThrows(NoSuchElementException.class,
                () -> servicio.eliminarHueco(
                        agenda.getId(), hueco.getId()));
    }

    @Test
    void rechazaAgregarHuecosAUnaAgendaInexistente() {
        assertThrows(NoSuchElementException.class,
                () -> servicio.agregarHueco(
                        "agenda-inexistente",
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0)));
    }
}