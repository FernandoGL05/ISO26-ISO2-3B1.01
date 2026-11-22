package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import es.uclm.esi.iso2.B101.servidor.dominio.EncuestaSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.dominio.RespuestaEncuestaSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioEncuestasSatisfaccionMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioRespuestasEncuestasMemoria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioEncuestasSatisfaccionTest {
    private RepositorioEncuestasSatisfaccionMemoria repositorioEncuestas;
    private ServicioEncuestasSatisfaccion servicio;

    @BeforeEach
    void prepararServicio() {
        repositorioEncuestas = new RepositorioEncuestasSatisfaccionMemoria();
        repositorioEncuestas.guardar(new EncuestaSatisfaccion("e1", "Atención", "Valore el servicio", true));
        repositorioEncuestas.guardar(new EncuestaSatisfaccion("e2", "Cerrada", "No disponible", false));
        servicio = new ServicioEncuestasSatisfaccion(
                repositorioEncuestas,
                new RepositorioRespuestasEncuestasMemoria(),
                Clock.fixed(Instant.parse("2026-11-22T09:15:00Z"), ZoneOffset.UTC));
    }

    @Test
    void consultaSoloEncuestasDisponiblesYGuardaRespuestaConFechaInyectada() {
        assertEquals(List.of("e1"), servicio.consultarEncuestasDisponibles().stream()
                .map(EncuestaSatisfaccion::getId)
                .toList());

        RespuestaEncuestaSatisfaccion respuesta = servicio.responderEncuesta("p1", "e1", 5, "Muy bien");

        assertEquals("e1", respuesta.getEncuestaId());
        assertEquals("p1", respuesta.getPacienteId());
        assertEquals(LocalDateTime.of(2026, 11, 22, 9, 15), respuesta.getFechaRespuesta());
        assertEquals(List.of(respuesta), servicio.consultarMisRespuestas("p1"));
    }

    @Test
    void noPermiteResponderEncuestasInexistentesONoDisponibles() {
        assertThrows(NoSuchElementException.class, () -> servicio.responderEncuesta("p1", "no-existe", 4, null));
        assertThrows(IllegalStateException.class, () -> servicio.responderEncuesta("p1", "e2", 4, null));
        assertEquals(List.of(), servicio.consultarMisRespuestas("p1"));
    }

    @Test
    void rechazaPuntuacionInvalidaYRespuestaDuplicada() {
        assertThrows(IllegalArgumentException.class, () -> servicio.responderEncuesta("p1", "e1", 6, null));
        servicio.responderEncuesta("p1", "e1", 4, null);
        assertThrows(IllegalStateException.class, () -> servicio.responderEncuesta("p1", "e1", 3, "Otra"));
    }

    @Test
    void aíslaRespuestasPorPacienteYPermiteResponderAEncuestaDistinta() {
        RespuestaEncuestaSatisfaccion primera = servicio.responderEncuesta("p1", "e1", 5, null);
        servicio.responderEncuesta("p2", "e1", 2, null);
        repositorioEncuestas.guardar(new EncuestaSatisfaccion("e3", "Otra", "Descripción", true));
        RespuestaEncuestaSatisfaccion segunda = servicio.responderEncuesta("p1", "e3", 3, null);

        assertEquals(
            Set.of(primera, segunda),
            Set.copyOf(servicio.consultarMisRespuestas("p1"))
            );
        assertNotEquals(primera.getPacienteId(), servicio.consultarMisRespuestas("p2").get(0).getPacienteId());
    }
}
