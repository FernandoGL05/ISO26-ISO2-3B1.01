package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class HorarioOperativoTest {

    @Test
    void detectaSolapamientosEnMismoRecursoYDia() {
        HorarioOperativo manana = new HorarioOperativo(
                "HOR-1", "medico-1", TipoRecursoHorario.MEDICO, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(14, 0), 30, true);
        HorarioOperativo solapado = new HorarioOperativo(
                "HOR-2", "MEDICO-1", TipoRecursoHorario.MEDICO, DayOfWeek.MONDAY,
                LocalTime.of(13, 0), LocalTime.of(15, 0), 30, true);
        HorarioOperativo contiguo = new HorarioOperativo(
                "HOR-3", "medico-1", TipoRecursoHorario.MEDICO, DayOfWeek.MONDAY,
                LocalTime.of(14, 0), LocalTime.of(18, 0), 30, true);
        HorarioOperativo otroDia = new HorarioOperativo(
                "HOR-4", "medico-1", TipoRecursoHorario.MEDICO, DayOfWeek.TUESDAY,
                LocalTime.of(9, 0), LocalTime.of(14, 0), 30, true);
        HorarioOperativo otroRecurso = new HorarioOperativo(
                "HOR-5", "sala-1", TipoRecursoHorario.SALA, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(14, 0), 30, true);

        assertTrue(manana.seSolapaCon(solapado));
        assertFalse(manana.seSolapaCon(contiguo));
        assertFalse(manana.seSolapaCon(otroDia));
        assertFalse(manana.seSolapaCon(otroRecurso));
        assertFalse(manana.seSolapaCon(null));

        manana.desactivar();
        assertFalse(manana.isActivo());
        manana.activar();
        assertTrue(manana.isActivo());
    }

    @Test
    void rechazaHorasInvertidasODuracionInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new HorarioOperativo(
                "HOR-1", "sala-1", TipoRecursoHorario.SALA, DayOfWeek.FRIDAY,
                LocalTime.of(14, 0), LocalTime.of(9, 0), 30, true));

        assertThrows(IllegalArgumentException.class, () -> new HorarioOperativo(
                "HOR-1", "sala-1", TipoRecursoHorario.SALA, DayOfWeek.FRIDAY,
                LocalTime.of(9, 0), LocalTime.of(14, 0), 0, true));

        assertThrows(IllegalArgumentException.class, () -> new HorarioOperativo(
                "HOR-1", " ", TipoRecursoHorario.EQUIPO, DayOfWeek.FRIDAY,
                LocalTime.of(9, 0), LocalTime.of(14, 0), 30, true));
    }
}
