package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.*;

import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoPrueba;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioTiposPruebaMemoria;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioTiposPruebaTest {

    private ServicioTiposPrueba servicio;

    @BeforeEach
    void setUp() {
        servicio = new ServicioTiposPrueba(new RepositorioTiposPruebaMemoria());
    }

    @Test
    void creaListaActualizaYCambiaEstadoDeTipoPrueba() {
        TipoPrueba creado = servicio.crearTipoPrueba(
                "Ecografía Abdominal", "Ecografía", "Ecógrafo Doppler", "Sala ECO-1",
                20, "Ayuno 6 horas", false, false,
                "Higiene de transductor", PrioridadPrueba.ORDINARIA, true);

        assertEquals("TP-1", creado.getId());
        assertTrue(creado.isActivo());
        assertEquals(1, servicio.listarTodos().size());
        assertEquals(1, servicio.listarActivos().size());

        TipoPrueba actualizado = servicio.actualizarTipoPrueba(
                "TP-1", "Ecografía Abdominal Completa", "Ecografía", "Ecógrafo HD", "Sala ECO-2",
                25, "Ayuno 8 horas", false, false,
                "Higiene de transductor", PrioridadPrueba.PREFERENTE, true);
        assertEquals("Ecografía Abdominal Completa", actualizado.getNombre());
        assertEquals(25, actualizado.getDuracionEstimadaMinutos());

        TipoPrueba desactivado = servicio.cambiarEstado("TP-1", false);
        assertFalse(desactivado.isActivo());
        assertTrue(servicio.listarActivos().isEmpty());
        assertEquals(1, servicio.listarTodos().size());

        TipoPrueba reactivado = servicio.cambiarEstado("TP-1", true);
        assertTrue(reactivado.isActivo());
        assertEquals(1, servicio.listarActivos().size());
    }

    @Test
    void rechazaNombresDuplicadosYIdsInexistentes() {
        servicio.crearTipoPrueba(
                "Mamografía Bilateral", "Mamografía", "Mamógrafo Digital", "Sala M-1",
                15, "Sin desodorante", false, false,
                "Protección radiológica", PrioridadPrueba.ORDINARIA, true);

        assertThrows(IllegalStateException.class, () -> servicio.crearTipoPrueba(
                "mamografía bilateral", "Mamografía", "Mamógrafo", "Sala M-2",
                15, "Ninguna", false, false,
                "Protección", PrioridadPrueba.ORDINARIA, false));

        assertThrows(NoSuchElementException.class, () -> servicio.obtenerPorId("TP-999"));
        assertThrows(IllegalArgumentException.class, () -> servicio.obtenerPorId(" "));
    }
}
