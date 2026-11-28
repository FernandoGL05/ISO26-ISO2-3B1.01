package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.*;

import es.uclm.esi.iso2.B101.servidor.dominio.Protocolo;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioProtocolosMemoria;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ServicioProtocolosTest {

    @Test
    void creaActualizaVersionYCambiaEstadoDeProtocolo() {
        ServicioProtocolos servicio = new ServicioProtocolos(new RepositorioProtocolosMemoria());

        Protocolo p = servicio.crearProtocolo(
                "PROT-TAC-01", "Protocolo TAC Craneal Simple", "TP-1",
                "Cortes axiales de 3 mm", "Sin contraste", "Control dosis", false);

        assertEquals("PROT-1", p.getId());
        assertEquals(1, p.getVersion());
        assertEquals(1, servicio.listarPorTipoPrueba("TP-1").size());

        Protocolo v2 = servicio.actualizarProtocolo(
                "PROT-1", "PROT-TAC-01", "Protocolo TAC Craneal con Contraste", "TP-1",
                "Cortes con contraste yodado", "Ayuno 4h", "Verificar creatinina", true);
        assertEquals(2, v2.getVersion());
        assertTrue(v2.isUsoContraste());

        Protocolo inactivo = servicio.cambiarEstado("PROT-1", false);
        assertFalse(inactivo.isActivo());
        Protocolo reactivado = servicio.cambiarEstado("PROT-1", true);
        assertTrue(reactivado.isActivo());
        assertEquals(1, servicio.listarTodos().size());
    }

    @Test
    void rechazaTipoPruebaInexistenteYCodigoDuplicado() {
        ServicioProtocolos servicioConPredicado = new ServicioProtocolos(
                new RepositorioProtocolosMemoria(),
                "TP-1"::equals);

        assertThrows(NoSuchElementException.class, () -> servicioConPredicado.crearProtocolo(
                "PROT-ERR", "Sin Tipo", "TP-INEXISTENTE",
                "Desc", "Prep", "Seg", false));

        servicioConPredicado.crearProtocolo(
                "PROT-UNICO", "Primero", "TP-1",
                "Desc", "Prep", "Seg", false);

        assertThrows(IllegalStateException.class, () -> servicioConPredicado.crearProtocolo(
                "prot-unico", "Duplicado", "TP-1",
                "Desc", "Prep", "Seg", false));

        assertThrows(NoSuchElementException.class, () -> servicioConPredicado.obtenerPorId("PROT-999"));
        assertThrows(IllegalArgumentException.class, () -> servicioConPredicado.listarPorTipoPrueba(" "));
    }
}
