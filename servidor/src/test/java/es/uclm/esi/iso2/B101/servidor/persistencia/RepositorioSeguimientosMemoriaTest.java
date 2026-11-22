package es.uclm.esi.iso2.B101.servidor.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositorioSeguimientosMemoriaTest {
    @Test
    void registraBuscaFiltraPorPacienteYNoExponeColeccionesModificables() {
        RepositorioSeguimientosMemoria repositorio = new RepositorioSeguimientosMemoria();
        SeguimientoPaciente propio = seguimiento("s1", "p1");
        repositorio.registrar(propio);
        repositorio.registrar(seguimiento("s2", "p2"));

        assertEquals(propio, repositorio.buscarPorId("s1").orElseThrow());
        assertEquals(List.of(propio), repositorio.buscarPorPacienteId("p1"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> repositorio.buscarPorPacienteId("p1").clear());
        assertThrows(IllegalStateException.class, () -> repositorio.registrar(propio));
    }

    private static SeguimientoPaciente seguimiento(String id, String pacienteId) {
        return new SeguimientoPaciente(id, pacienteId, "Ecografía", "Control", 6, null, null, null);
    }
}
