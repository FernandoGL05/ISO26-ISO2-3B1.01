package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProtocoloTest {

    @Test
    void creaYVersionaProtocoloCorrectamente() {
        Protocolo protocolo = new Protocolo(
                "PROT-1",
                "PR-RM-01",
                "Protocolo RM Cerebral",
                "TP-1",
                "Adquisición T1/T2/FLAIR",
                "Retirar prótesis metálicas",
                "Verificar implantes compatibles",
                false,
                1,
                true);

        assertEquals("PROT-1", protocolo.getId());
        assertEquals("PR-RM-01", protocolo.getCodigo());
        assertEquals("Protocolo RM Cerebral", protocolo.getNombre());
        assertEquals("TP-1", protocolo.getTipoPruebaId());
        assertEquals("Adquisición T1/T2/FLAIR", protocolo.getDescripcion());
        assertEquals("Retirar prótesis metálicas", protocolo.getPasosPreparacion());
        assertEquals("Verificar implantes compatibles", protocolo.getRequisitosSeguridad());
        assertFalse(protocolo.isUsoContraste());
        assertEquals(1, protocolo.getVersion());
        assertTrue(protocolo.isActivo());

        protocolo.desactivar();
        assertFalse(protocolo.isActivo());
        protocolo.activar();
        assertTrue(protocolo.isActivo());

        Protocolo v2 = protocolo.conNuevaVersion(
                "PR-RM-01",
                "Protocolo RM Cerebral con Contraste",
                "TP-1",
                "Incluye gadolinio",
                "Ayuno de 4 horas",
                "Test de creatinina",
                true);
        assertEquals(2, v2.getVersion());
        assertTrue(v2.isUsoContraste());
        assertEquals("Protocolo RM Cerebral con Contraste", v2.getNombre());
    }

    @Test
    void rechazaCamposVaciosOVersionInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new Protocolo(
                "PROT-1", "", "Nombre", "TP-1", "Desc", "Prep", "Seg", false, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new Protocolo(
                "PROT-1", "COD", "Nombre", "TP-1", "Desc", "Prep", "Seg", false, 0, true));
    }
}
