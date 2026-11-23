package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoCita;
import es.uclm.esi.iso2.B101.servidor.dominio.Incidencia;
import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidencias;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public final class ServicioRealizacionPruebas {
    private static final String ASUNTO_INCIDENCIA = "Incidencia durante realización de prueba";

    private final RepositorioCitas repositorioCitas;
    private final RepositorioHistorialPruebas repositorioHistorial;
    private final RepositorioIncidencias repositorioIncidencias;
    private final Clock reloj;

    public ServicioRealizacionPruebas(
            RepositorioCitas repositorioCitas,
            RepositorioHistorialPruebas repositorioHistorial,
            RepositorioIncidencias repositorioIncidencias) {
        this(repositorioCitas, repositorioHistorial, repositorioIncidencias, Clock.systemDefaultZone());
    }

    public ServicioRealizacionPruebas(
            RepositorioCitas repositorioCitas,
            RepositorioHistorialPruebas repositorioHistorial,
            RepositorioIncidencias repositorioIncidencias,
            Clock reloj) {
        this.repositorioCitas = Objects.requireNonNull(repositorioCitas, "repositorioCitas no puede ser nulo");
        this.repositorioHistorial =
                Objects.requireNonNull(repositorioHistorial, "repositorioHistorial no puede ser nulo");
        this.repositorioIncidencias =
                Objects.requireNonNull(repositorioIncidencias, "repositorioIncidencias no puede ser nulo");
        this.reloj = Objects.requireNonNull(reloj, "reloj no puede ser nulo");
    }

    public PruebaRealizada registrarRealizacion(String medicoId, String citaId) {
        Cita cita = buscarCitaAsignada(medicoId, citaId);
        validarPendiente(cita);
        PruebaRealizada prueba = new PruebaRealizada(
                UUID.randomUUID().toString(),
                cita.getPacienteId(),
                cita.getTipoPrueba(),
                LocalDateTime.now(reloj),
                null,
                cita.getId());
        repositorioHistorial.registrar(prueba);
        cita.marcarRealizada();
        repositorioCitas.guardar(cita);
        return prueba;
    }

    public Incidencia registrarIncidencia(String medicoId, String citaId, String descripcion) {
        Cita cita = buscarCitaAsignada(medicoId, citaId);
        validarPendiente(cita);
        Incidencia incidencia = new Incidencia(
                UUID.randomUUID().toString(),
                cita.getPacienteId(),
                ASUNTO_INCIDENCIA,
                descripcion,
                cita.getId(),
                LocalDateTime.now(reloj));
        repositorioIncidencias.guardar(incidencia);
        return incidencia;
    }

    private Cita buscarCitaAsignada(String medicoId, String citaId) {
        validarIdentificador(medicoId, "medicoId");
        validarIdentificador(citaId, "citaId");
        Cita cita = repositorioCitas.buscarPorId(citaId)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada"));
        if (!medicoId.equals(cita.getMedicoId())) {
            throw new NoSuchElementException("Cita no encontrada");
        }
        return cita;
    }

    private void validarPendiente(Cita cita) {
        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new IllegalStateException("No se puede registrar un resultado para una cita cancelada");
        }
        if (cita.getEstado() == EstadoCita.REALIZADA
                || repositorioHistorial.buscarPorCitaId(cita.getId()).isPresent()) {
            throw new IllegalStateException("La cita ya tiene una prueba realizada registrada");
        }
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
