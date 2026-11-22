package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EncuestaSatisfaccionTest {
    @Test
    void representaUnaEncuestaDisponibleOSinDisponibilidad() {
        EncuestaSatisfaccion encuesta = new EncuestaSatisfaccion("e1", "Atención", "Valore el servicio", true);

        assertEquals("e1", encuesta.getId());
        assertEquals("Atención", encuesta.getTitulo());
        assertEquals("Valore el servicio", encuesta.getDescripcion());
        assertTrue(encuesta.estaDisponible());
        assertFalse(new EncuestaSatisfaccion("e2", "Otra", "Descripción", false).estaDisponible());
    }

    @Test
    void exigeIdentificadorTituloYDescripcion() {
        assertThrows(IllegalArgumentException.class, () -> new EncuestaSatisfaccion(null, "t", "d", true));
        assertThrows(IllegalArgumentException.class, () -> new EncuestaSatisfaccion(" ", "t", "d", true));
        assertThrows(IllegalArgumentException.class, () -> new EncuestaSatisfaccion("e1", "", "d", true));
        assertThrows(IllegalArgumentException.class, () -> new EncuestaSatisfaccion("e1", "t", null, true));
    }

    @Test
    void validaLosCamposYLimitesDeLaRespuesta() {
        LocalDateTime fecha = LocalDateTime.of(2026, 11, 22, 10, 0);
        RespuestaEncuestaSatisfaccion respuesta =
                new RespuestaEncuestaSatisfaccion("r1", "e1", "p1", 1, "Comentario", fecha);

        assertEquals(1, respuesta.getPuntuacion());
        assertEquals("Comentario", respuesta.getComentario());
        assertEquals(fecha, respuesta.getFechaRespuesta());
        assertEquals(5, new RespuestaEncuestaSatisfaccion("r2", "e1", "p1", 5, null, fecha).getPuntuacion());
        assertEquals(
                RespuestaEncuestaSatisfaccion.LONGITUD_MAXIMA_COMENTARIO,
                new RespuestaEncuestaSatisfaccion(
                                "r3",
                                "e1",
                                "p1",
                                3,
                                "x".repeat(RespuestaEncuestaSatisfaccion.LONGITUD_MAXIMA_COMENTARIO),
                                fecha)
                        .getComentario()
                        .length());

        assertThrows(IllegalArgumentException.class, () -> new RespuestaEncuestaSatisfaccion(
                null, "e1", "p1", 3, null, fecha));
        assertThrows(IllegalArgumentException.class, () -> new RespuestaEncuestaSatisfaccion(
                "r4", "", "p1", 3, null, fecha));
        assertThrows(IllegalArgumentException.class, () -> new RespuestaEncuestaSatisfaccion(
                "r5", "e1", " ", 3, null, fecha));
        assertThrows(IllegalArgumentException.class, () -> new RespuestaEncuestaSatisfaccion(
                "r6", "e1", "p1", 0, null, fecha));
        assertThrows(IllegalArgumentException.class, () -> new RespuestaEncuestaSatisfaccion(
                "r7", "e1", "p1", 6, null, fecha));
        assertThrows(IllegalArgumentException.class, () -> new RespuestaEncuestaSatisfaccion(
                "r8", "e1", "p1", 3, "x".repeat(2001), fecha));
        assertThrows(IllegalArgumentException.class, () -> new RespuestaEncuestaSatisfaccion(
                "r9", "e1", "p1", 3, null, null));
    }
}
