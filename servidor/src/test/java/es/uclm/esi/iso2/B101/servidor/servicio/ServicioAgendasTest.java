package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioAgendasTest {
    private static final LocalDateTime DESDE = LocalDateTime.of(2026, 12, 1, 9, 0);
    private static final LocalDateTime HASTA = LocalDateTime.of(2026, 12, 1, 11, 0);

    private RepositorioCitasMemoria repositorio;
    private ServicioAgendas servicio;

    @BeforeEach
    void crearServicio() {
        repositorio = new RepositorioCitasMemoria();
        servicio = new ServicioAgendas(repositorio);
    }

    @Test
    void calculaHuecosLibresYExcluyeCitasDeOtrosMedicosYCitasCanceladas() {
        repositorio.guardar(new Cita("cita-1", "paciente-1", "medico-1", "RM", DESDE.plusMinutes(30)));
        repositorio.guardar(new Cita("cita-2", "paciente-2", "medico-2", "RM", DESDE.plusMinutes(60)));
        Cita cancelada = new Cita("cita-3", "paciente-3", "medico-1", "TAC", DESDE.plusMinutes(90));
        cancelada.confirmar();
        cancelada.cancelar();
        repositorio.guardar(cancelada);

        List<ServicioAgendas.HuecoDisponible> huecos =
                servicio.consultarDisponibilidad("medico-1", "RM", DESDE, HASTA, 30);

        assertEquals(
                List.of(
                        new ServicioAgendas.HuecoDisponible(
                                "RM", DESDE, DESDE.plusMinutes(30)),
                        new ServicioAgendas.HuecoDisponible(
                                "RM", DESDE.plusMinutes(60), DESDE.plusMinutes(90)),
                        new ServicioAgendas.HuecoDisponible(
                                "RM", DESDE.plusMinutes(90), HASTA)),
                huecos);
    }

    @Test
    void rechazaIdentificadoresIntervalosYDuracionesInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultarDisponibilidad("", "RM", DESDE, HASTA, 30));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultarDisponibilidad("medico-1", " ", DESDE, HASTA, 30));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultarDisponibilidad(
                        "medico-1", "RM", DESDE, DESDE, 30));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultarDisponibilidad(
                        "medico-1", "RM", DESDE, HASTA.plusDays(1), 30));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultarDisponibilidad("medico-1", "RM", DESDE, HASTA, 0));
    }
}
