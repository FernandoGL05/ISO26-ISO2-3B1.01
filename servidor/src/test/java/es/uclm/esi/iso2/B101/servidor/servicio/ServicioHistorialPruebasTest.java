package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioHistorialPruebasTest {
    private ServicioHistorialPruebas servicio;
    private RepositorioHistorialPruebasMemoria repositorio;

    @BeforeEach
    void prepararServicio() {
        repositorio = new RepositorioHistorialPruebasMemoria();
        servicio = new ServicioHistorialPruebas(repositorio);
    }

    @Test
    void registraUnaPruebaRealizadaYPermiteBuscarlaPorId() {
        PruebaRealizada prueba = servicio.registrarPruebaRealizada(
                "prueba-1",
                "paciente-1",
                "Radiografía",
                LocalDateTime.of(2026, 3, 15, 10, 30),
                "solicitud-1");

        assertEquals(prueba, repositorio.buscarPorId("prueba-1").orElseThrow());
        assertEquals(List.of(prueba), servicio.consultarHistorial("paciente-1"));
    }

    @Test
    void impideRegistrarDosPruebasConElMismoIdentificador() {
        servicio.registrarPruebaRealizada(
                "prueba-1", "paciente-1", "Radiografía", LocalDateTime.of(2026, 3, 15, 10, 30), null);

        assertThrows(
                IllegalStateException.class,
                () -> servicio.registrarPruebaRealizada(
                        "prueba-1", "paciente-1", "Tomografía", LocalDateTime.of(2026, 3, 16, 10, 30), null));
        assertEquals(1, servicio.consultarHistorial("paciente-1").size());
    }

    @Test
    void devuelveElHistorialOrdenadoDeMasRecienteAMasAntiguo() {
        PruebaRealizada antigua = servicio.registrarPruebaRealizada(
                "prueba-antigua",
                "paciente-1",
                "Radiografía",
                LocalDateTime.of(2025, 1, 1, 8, 0),
                null);
        PruebaRealizada reciente = servicio.registrarPruebaRealizada(
                "prueba-reciente",
                "paciente-1",
                "Tomografía",
                LocalDateTime.of(2026, 1, 1, 8, 0),
                null);
        PruebaRealizada intermedia = servicio.registrarPruebaRealizada(
                "prueba-intermedia",
                "paciente-1",
                "Ecografía",
                LocalDateTime.of(2025, 6, 1, 8, 0),
                null);

        assertEquals(List.of(reciente, intermedia, antigua), servicio.consultarHistorial("paciente-1"));
    }

    @Test
    void mantieneLosHistorialesSeparadosYDevuelveUnaListaIndependiente() {
        PruebaRealizada propia = servicio.registrarPruebaRealizada(
                "prueba-1", "paciente-1", "Radiografía", LocalDateTime.of(2026, 3, 15, 10, 30), null);
        servicio.registrarPruebaRealizada(
                "prueba-2", "paciente-2", "Tomografía", LocalDateTime.of(2026, 3, 16, 10, 30), null);

        List<PruebaRealizada> historial = servicio.consultarHistorial("paciente-1");
        assertEquals(List.of(propia), historial);
        assertThrows(UnsupportedOperationException.class, () -> historial.clear());
        assertEquals(1, servicio.consultarHistorial("paciente-1").size());
        assertTrue(servicio.consultarHistorial("paciente-sin-pruebas").isEmpty());
    }

    @Test
    void rechazaUnIdentificadorDePacienteInvalido() {
        assertThrows(IllegalArgumentException.class, () -> servicio.consultarHistorial(" "));
    }
}
