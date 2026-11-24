package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.*;

import es.uclm.esi.iso2.B101.servidor.dominio.HorarioOperativo;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoRecursoHorario;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHorariosMemoria;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioHorariosTest {

    private ServicioHorarios servicio;

    @BeforeEach
    void setUp() {
        servicio = new ServicioHorarios(new RepositorioHorariosMemoria());
    }

    @Test
    void creaActualizaYFiltraHorariosSinSolapamientos() {
        HorarioOperativo h1 = servicio.crearHorario(
                "medico-1", TipoRecursoHorario.MEDICO, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
        HorarioOperativo h2 = servicio.crearHorario(
                "medico-1", TipoRecursoHorario.MEDICO, DayOfWeek.MONDAY,
                LocalTime.of(12, 0), LocalTime.of(14, 0), 30);

        assertEquals("HOR-1", h1.getId());
        assertEquals("HOR-2", h2.getId());
        assertEquals(2, servicio.listarPorRecurso("medico-1").size());
        assertEquals(2, servicio.listarActivosPorRecursoYDia("medico-1", DayOfWeek.MONDAY).size());

        HorarioOperativo actualizado = servicio.actualizarHorario(
                "HOR-2", "medico-1", TipoRecursoHorario.MEDICO, DayOfWeek.MONDAY,
                LocalTime.of(13, 0), LocalTime.of(15, 0), 30);
        assertEquals(LocalTime.of(13, 0), actualizado.getHoraInicio());

        servicio.cambiarEstado("HOR-2", false);
        assertEquals(1, servicio.listarActivosPorRecursoYDia("medico-1", DayOfWeek.MONDAY).size());
        assertEquals(2, servicio.listarTodos().size());
    }

    @Test
    void impideSolapamientosAlCrearYAlReactivar() {
        servicio.crearHorario(
                "sala-tac-1", TipoRecursoHorario.SALA, DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(12, 0), 20);

        assertThrows(IllegalStateException.class, () -> servicio.crearHorario(
                "sala-tac-1", TipoRecursoHorario.SALA, DayOfWeek.WEDNESDAY,
                LocalTime.of(11, 0), LocalTime.of(14, 0), 20));

        HorarioOperativo tarde = servicio.crearHorario(
                "sala-tac-1", TipoRecursoHorario.SALA, DayOfWeek.WEDNESDAY,
                LocalTime.of(12, 0), LocalTime.of(15, 0), 20);
        servicio.cambiarEstado(tarde.getId(), false);

        // Creamos uno en la franja desactivada; luego al intentar reactivar el antiguo debe fallar
        servicio.crearHorario(
                "sala-tac-1", TipoRecursoHorario.SALA, DayOfWeek.WEDNESDAY,
                LocalTime.of(12, 30), LocalTime.of(14, 30), 20);

        assertThrows(IllegalStateException.class, () -> servicio.cambiarEstado(tarde.getId(), true));
        assertThrows(NoSuchElementException.class, () -> servicio.obtenerPorId("HOR-999"));
    }
}
