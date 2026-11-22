package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSeguimientosMemoria;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioSeguimientosTest {
    private RepositorioSeguimientosMemoria repositorio;
    private ServicioSeguimientos servicio;

    @BeforeEach
    void prepararServicio() {
        repositorio = new RepositorioSeguimientosMemoria();
        servicio = new ServicioSeguimientos(repositorio);
    }

    @Test
    void devuelveListaVaciaCuandoPacienteNoTieneSeguimientos() {
        assertEquals(List.of(), servicio.consultarSeguimientos("p1"));
    }

    @Test
    void registraYOrdenaPorProximaRevisionConFechasAusentesAlFinal() {
        SeguimientoPaciente sinFecha = crear("s0", "p1", null);
        SeguimientoPaciente posterior = crear("s2", "p1", LocalDate.of(2027, 5, 1));
        SeguimientoPaciente anterior = crear("s1", "p1", LocalDate.of(2027, 2, 1));
        servicio.registrarSeguimiento(sinFecha);
        servicio.registrarSeguimiento(posterior);
        servicio.registrarSeguimiento(anterior);
        servicio.registrarSeguimiento(crear("ajeno", "p2", LocalDate.of(2026, 1, 1)));

        assertEquals(List.of(anterior, posterior, sinFecha), servicio.consultarSeguimientos("p1"));
    }

    @Test
    void consultaDetallePropioYOcultaSeguimientosAjenosOInexistentes() {
        SeguimientoPaciente propio = crear("s1", "p1", LocalDate.of(2027, 1, 1));
        servicio.registrarSeguimiento(propio);
        servicio.registrarSeguimiento(crear("s2", "p2", null));

        assertEquals(propio, servicio.consultarDetalle("p1", "s1"));
        assertThrows(NoSuchElementException.class, () -> servicio.consultarDetalle("p1", "s2"));
        assertThrows(NoSuchElementException.class, () -> servicio.consultarDetalle("p1", "ausente"));
    }

    private static SeguimientoPaciente crear(String id, String pacienteId, LocalDate fechaProxima) {
        return new SeguimientoPaciente(
                id, pacienteId, "Resonancia", "Seguimiento", 12, null, fechaProxima, "Preparación indicada");
    }
}
