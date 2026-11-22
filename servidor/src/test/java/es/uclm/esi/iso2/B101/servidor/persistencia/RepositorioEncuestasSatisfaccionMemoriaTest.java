package es.uclm.esi.iso2.B101.servidor.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import es.uclm.esi.iso2.B101.servidor.dominio.EncuestaSatisfaccion;
import org.junit.jupiter.api.Test;

class RepositorioEncuestasSatisfaccionMemoriaTest {
    @Test
    void recuperaEncuestasPorIdYListaSoloLasDisponibles() {
        RepositorioEncuestasSatisfaccionMemoria repositorio = new RepositorioEncuestasSatisfaccionMemoria();
        EncuestaSatisfaccion disponible = new EncuestaSatisfaccion("e1", "A", "Descripción", true);
        repositorio.guardar(new EncuestaSatisfaccion("e2", "B", "Descripción", false));
        repositorio.guardar(disponible);

        assertEquals(disponible, repositorio.buscarPorId("e1").orElseThrow());
        assertEquals(java.util.List.of(disponible), repositorio.buscarDisponibles());
        assertThrows(IllegalStateException.class, () -> repositorio.guardar(disponible));
    }
}
