package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebas;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ServicioHistorialPruebas {
    private static final Comparator<PruebaRealizada> ORDEN_MAS_RECIENTE_PRIMERO =
            Comparator.comparing(PruebaRealizada::getFechaHoraRealizacion)
                    .reversed()
                    .thenComparing(PruebaRealizada::getId);

    private final RepositorioHistorialPruebas repositorio;

    public ServicioHistorialPruebas(RepositorioHistorialPruebas repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio no puede ser nulo");
    }

    public PruebaRealizada registrarPruebaRealizada(
            String id,
            String pacienteId,
            String tipoPrueba,
            LocalDateTime fechaHoraRealizacion,
            String solicitudId) {
        PruebaRealizada prueba =
                new PruebaRealizada(id, pacienteId, tipoPrueba, fechaHoraRealizacion, solicitudId);
        repositorio.registrar(prueba);
        return prueba;
    }

    public List<PruebaRealizada> consultarHistorial(String pacienteId) {
        if (pacienteId == null || pacienteId.isBlank()) {
            throw new IllegalArgumentException("pacienteId no puede ser nulo ni estar vacío");
        }
        return repositorio.buscarPorPacienteId(pacienteId).stream()
                .sorted(ORDEN_MAS_RECIENTE_PRIMERO)
                .toList();
    }
}
