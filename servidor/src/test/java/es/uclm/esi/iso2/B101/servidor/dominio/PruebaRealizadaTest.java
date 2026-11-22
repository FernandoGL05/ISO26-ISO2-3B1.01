package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PruebaRealizadaTest {
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 3, 15, 10, 30);

    @Test
    void creaPruebaRealizadaConDatosBasicosYRelacionOpcional() {
        PruebaRealizada prueba =
                new PruebaRealizada("prueba-1", "paciente-1", "Radiografía", FECHA, null);

        assertEquals("prueba-1", prueba.getId());
        assertEquals("paciente-1", prueba.getPacienteId());
        assertEquals("Radiografía", prueba.getTipoPrueba());
        assertEquals(FECHA, prueba.getFechaHoraRealizacion());
        assertNull(prueba.getSolicitudId());
        assertNull(prueba.getCitaId());
    }

    @Test
    void admiteVinculoOpcionalConCita() {
        PruebaRealizada prueba =
                new PruebaRealizada("prueba-1", "paciente-1", "RM", FECHA, null, "cita-1");

        assertEquals("cita-1", prueba.getCitaId());
    }

    @Test
    void rechazaIdentificadorDeCitaVacio() {
        assertThrows(IllegalArgumentException.class, () -> new PruebaRealizada(
                "prueba-1", "paciente-1", "RM", FECHA, null, " "));
    }

    @Test
    void rechazaIdentificadoresYCamposObligatoriosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new PruebaRealizada(
                "", "paciente-1", "Radiografía", FECHA, null));
        assertThrows(IllegalArgumentException.class, () -> new PruebaRealizada(
                "prueba-1", " ", "Radiografía", FECHA, null));
        assertThrows(IllegalArgumentException.class, () -> new PruebaRealizada(
                "prueba-1", "paciente-1", "", FECHA, null));
        assertThrows(IllegalArgumentException.class, () -> new PruebaRealizada(
                "prueba-1", "paciente-1", "Radiografía", null, null));
        assertThrows(IllegalArgumentException.class, () -> new PruebaRealizada(
                "prueba-1", "paciente-1", "Radiografía", FECHA, " "));
    }
}
