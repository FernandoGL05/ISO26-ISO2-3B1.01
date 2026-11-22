package es.uclm.esi.iso2.B101.servidor.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.RespuestaEncuestaSatisfaccion;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RepositorioRespuestasEncuestasMemoriaTest {
    @Test
    void mantieneUnaRespuestaPorEncuestaYPacienteYPermitePacientesDistintos() {
        RepositorioRespuestasEncuestasMemoria repositorio = new RepositorioRespuestasEncuestasMemoria();
        LocalDateTime fecha = LocalDateTime.of(2026, 11, 22, 10, 0);
        RespuestaEncuestaSatisfaccion propia =
                new RespuestaEncuestaSatisfaccion("r1", "e1", "p1", 5, null, fecha);
        RespuestaEncuestaSatisfaccion ajena =
                new RespuestaEncuestaSatisfaccion("r2", "e1", "p2", 4, null, fecha);

        assertTrue(repositorio.guardarSiNoExiste(propia));
        assertFalse(repositorio.guardarSiNoExiste(
                new RespuestaEncuestaSatisfaccion("r3", "e1", "p1", 2, null, fecha)));
        assertFalse(repositorio.guardarSiNoExiste(
                new RespuestaEncuestaSatisfaccion("r1", "e2", "p3", 2, null, fecha)));
        assertTrue(repositorio.guardarSiNoExiste(ajena));
        assertEquals(propia, repositorio.buscarPorEncuestaYPaciente("e1", "p1").orElseThrow());
        assertEquals(java.util.List.of(propia), repositorio.buscarPorPaciente("p1"));
    }
}
