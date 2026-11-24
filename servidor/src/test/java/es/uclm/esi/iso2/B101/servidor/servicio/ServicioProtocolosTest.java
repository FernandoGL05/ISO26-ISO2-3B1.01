package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.*;

import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.Protocolo;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoPrueba;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioProtocolosMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioTiposPruebaMemoria;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioProtocolosTest {

    private ServicioTiposPrueba servicioTipos;
    private ServicioProtocolos servicioProtocolos;
    private TipoPrueba tipoPrueba;

    @BeforeEach
    void setUp() {
        RepositorioTiposPruebaMemoria repoTipos = new RepositorioTiposPruebaMemoria();
        servicioTipos = new ServicioTiposPrueba(repoTipos);
        servicioProtocolos = new ServicioProtocolos(new RepositorioProtocolosMemoria(), repoTipos);
        tipoPrueba = servicioTipos.crearTipoPrueba(
                "TAC Craneal", "TAC", "Tomógrafo", "Sala TAC-1",
                20, "Retirar pendientes", true, true,
                "Evaluar alergias", PrioridadPrueba.URGENTE, false);
    }

    @Test
    void creaActualizaVersionYCambiaEstadoDeProtocolo() {
        Protocolo p = servicioProtocolos.crearProtocolo(
                "PROT-TAC-01", "Protocolo TAC Craneal Simple", tipoPrueba.getId(),
                "Cortes axiales de 3 mm", "Sin contraste", "Control dosis", false);

        assertEquals("PROT-1", p.getId());
        assertEquals(1, p.getVersion());
        assertEquals(1, servicioProtocolos.listarPorTipoPrueba(tipoPrueba.getId()).size());

        Protocolo v2 = servicioProtocolos.actualizarProtocolo(
                "PROT-1", "PROT-TAC-01", "Protocolo TAC Craneal con Contraste", tipoPrueba.getId(),
                "Cortes con contraste yodado", "Ayuno 4h", "Verificar creatinina", true);
        assertEquals(2, v2.getVersion());
        assertTrue(v2.isUsoContraste());

        Protocolo inactivo = servicioProtocolos.cambiarEstado("PROT-1", false);
        assertFalse(inactivo.isActivo());
        assertEquals(1, servicioProtocolos.listarTodos().size());
    }

    @Test
    void rechazaTipoPruebaInexistenteYCodigoDuplicado() {
        assertThrows(NoSuchElementException.class, () -> servicioProtocolos.crearProtocolo(
                "PROT-ERR", "Sin Tipo", "TP-INEXISTENTE",
                "Desc", "Prep", "Seg", false));

        servicioProtocolos.crearProtocolo(
                "PROT-UNICO", "Primero", tipoPrueba.getId(),
                "Desc", "Prep", "Seg", false);

        assertThrows(IllegalStateException.class, () -> servicioProtocolos.crearProtocolo(
                "prot-unico", "Duplicado", tipoPrueba.getId(),
                "Desc", "Prep", "Seg", false));

        assertThrows(NoSuchElementException.class, () -> servicioProtocolos.obtenerPorId("PROT-999"));
    }
}
