package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSeguimientos;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class ServicioSeguimientos {
    private static final Comparator<SeguimientoPaciente> ORDEN_POR_PROXIMA_REVISION =
            Comparator.comparing(SeguimientoPaciente::getFechaProximaRevision,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(SeguimientoPaciente::getId);

    private final RepositorioSeguimientos repositorio;

    public ServicioSeguimientos(RepositorioSeguimientos repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio no puede ser nulo");
    }

    public SeguimientoPaciente registrarSeguimiento(SeguimientoPaciente seguimiento) {
        Objects.requireNonNull(seguimiento, "seguimiento no puede ser nulo");
        repositorio.registrar(seguimiento);
        return seguimiento;
    }

    public List<SeguimientoPaciente> consultarSeguimientos(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return repositorio.buscarPorPacienteId(pacienteId).stream()
                .sorted(ORDEN_POR_PROXIMA_REVISION)
                .toList();
    }

    public SeguimientoPaciente consultarDetalle(String pacienteId, String seguimientoId) {
        validarIdentificador(pacienteId, "pacienteId");
        validarIdentificador(seguimientoId, "seguimientoId");
        SeguimientoPaciente seguimiento = repositorio.buscarPorId(seguimientoId)
                .orElseThrow(() -> new NoSuchElementException("Seguimiento no encontrado"));
        if (!pacienteId.equals(seguimiento.getPacienteId())) {
            throw new NoSuchElementException("Seguimiento no encontrado");
        }
        return seguimiento;
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
