package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TipoPruebaTest {

    @Test
    void creaYActualizaTipoPruebaConTodosLosAtributos() {
        TipoPrueba tipo = new TipoPrueba(
                "TP-1",
                "TAC Abdominal",
                "TAC",
                "Tomógrafo 64 cortes",
                "Sala TAC-1",
                30,
                "Ayuno de 6 horas",
                true,
                true,
                "Control de función renal previo",
                PrioridadPrueba.PREFERENTE,
                true,
                true);

        assertEquals("TP-1", tipo.getId());
        assertEquals("TAC Abdominal", tipo.getNombre());
        assertEquals("TAC", tipo.getModalidad());
        assertEquals("Tomógrafo 64 cortes", tipo.getEquipoRequerido());
        assertEquals("Sala TAC-1", tipo.getSalaRequerida());
        assertEquals(30, tipo.getDuracionEstimadaMinutos());
        assertEquals("Ayuno de 6 horas", tipo.getPreparacionPrevia());
        assertTrue(tipo.isRequiereContraste());
        assertTrue(tipo.isRequiereConsentimientoInformado());
        assertEquals("Control de función renal previo", tipo.getRequisitosSeguridad());
        assertEquals(PrioridadPrueba.PREFERENTE, tipo.getPrioridad());
        assertTrue(tipo.isAdmiteSeguimientoPeriodico());
        assertTrue(tipo.isActivo());

        tipo.desactivar();
        assertFalse(tipo.isActivo());
        tipo.activar();
        assertTrue(tipo.isActivo());

        TipoPrueba actualizado = tipo.conActualizacion(
                "TAC Torácico",
                "TAC",
                "Tomógrafo 128 cortes",
                "Sala TAC-2",
                20,
                "Sin preparación especial",
                false,
                false,
                "Protección plomada",
                PrioridadPrueba.URGENTE,
                false);
        assertEquals("TP-1", actualizado.getId());
        assertEquals("TAC Torácico", actualizado.getNombre());
        assertEquals(20, actualizado.getDuracionEstimadaMinutos());
        assertEquals(PrioridadPrueba.URGENTE, actualizado.getPrioridad());
        assertFalse(actualizado.isRequiereContraste());
    }

    @Test
    void exigeConsentimientoSiRequiereContrasteYValidaLimites() {
        assertThrows(IllegalArgumentException.class, () -> new TipoPrueba(
                "TP-1", "RM Cerebral", "RM", "RM 3T", "Sala RM-1", 45,
                "Retirar objetos metálicos", true, false,
                "Sin marcapasos", PrioridadPrueba.ORDINARIA, true, true));

        assertThrows(IllegalArgumentException.class, () -> new TipoPrueba(
                "TP-1", " ", "RM", "RM 3T", "Sala RM-1", 45,
                "Prep", false, false, "Seg", PrioridadPrueba.ORDINARIA, false, true));

        assertThrows(IllegalArgumentException.class, () -> new TipoPrueba(
                "TP-1", "RM", "RM", "RM 3T", "Sala RM-1", 0,
                "Prep", false, false, "Seg", PrioridadPrueba.ORDINARIA, false, true));

        assertThrows(IllegalArgumentException.class, () -> new TipoPrueba(
                "TP-1", "RM", "RM", "RM 3T", "Sala RM-1", 1441,
                "Prep", false, false, "Seg", PrioridadPrueba.ORDINARIA, false, true));

        assertThrows(NullPointerException.class, () -> new TipoPrueba(
                "TP-1", "RM", "RM", "RM 3T", "Sala RM-1", 30,
                "Prep", false, false, "Seg", null, false, true));
    }
}
