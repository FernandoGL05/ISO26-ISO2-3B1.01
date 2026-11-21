package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.Incidencia;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidencias;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public final class ServicioIncidencias {
    private final RepositorioIncidencias repositorio;
    private final Clock reloj;

    public ServicioIncidencias(RepositorioIncidencias repositorio) {
        this(repositorio, Clock.systemDefaultZone());
    }

    public ServicioIncidencias(RepositorioIncidencias repositorio, Clock reloj) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio no puede ser nulo");
        this.reloj = Objects.requireNonNull(reloj, "reloj no puede ser nulo");
    }

    public Incidencia comunicarIncidencia(String pacienteId, String asunto, String descripcion, String citaId) {
        Incidencia incidencia = new Incidencia(
                UUID.randomUUID().toString(),
                pacienteId,
                asunto,
                descripcion,
                citaId,
                LocalDateTime.now(reloj));
        repositorio.guardar(incidencia);
        return incidencia;
    }

    public List<Incidencia> consultarIncidencias(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return repositorio.buscarPorPacienteId(pacienteId);
    }

    public Incidencia consultarDetalle(String pacienteId, String incidenciaId) {
        validarIdentificador(pacienteId, "pacienteId");
        validarIdentificador(incidenciaId, "incidenciaId");
        Incidencia incidencia = repositorio.buscarPorId(incidenciaId)
                .orElseThrow(() -> new NoSuchElementException("Incidencia no encontrada"));
        if (!pacienteId.equals(incidencia.getPacienteId())) {
            throw new NoSuchElementException("Incidencia no encontrada");
        }
        return incidencia;
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
