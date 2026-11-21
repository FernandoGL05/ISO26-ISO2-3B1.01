package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoCita;
import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudCita;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoSolicitudCita;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSolicitudesCita;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSolicitudesCitaMemoria;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public class ServicioCitas {
    private final RepositorioCitas repositorio;
    private final RepositorioSolicitudesCita repositorioSolicitudes;

    public ServicioCitas(RepositorioCitas repositorio) {
        this(repositorio, new RepositorioSolicitudesCitaMemoria());
    }

    public ServicioCitas(RepositorioCitas repositorio, RepositorioSolicitudesCita repositorioSolicitudes) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.repositorioSolicitudes = Objects.requireNonNull(repositorioSolicitudes);
    }

   public List<Cita> consultarCitas(String pacienteId) {
    validarIdentificador(pacienteId, "pacienteId");
    return repositorio.buscarPorPacienteId(pacienteId);
    }

    public void confirmarCita(String pacienteId, String citaId) {
        validarIdentificador(pacienteId, "pacienteId");
        validarIdentificador(citaId, "citaId");

        Cita cita = repositorio.buscarPorId(citaId)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada"));
        if (!pacienteId.equals(cita.getPacienteId())) {
            throw new NoSuchElementException("Cita no encontrada");
        }

        cita.confirmar();
        repositorio.guardar(cita);
    }

    public SolicitudCita solicitarCambioFecha(String pacienteId, String citaId, LocalDateTime nuevaFechaHora) {
        validarIdentificador(pacienteId, "pacienteId");
        validarIdentificador(citaId, "citaId");
        if (nuevaFechaHora == null) {
            throw new IllegalArgumentException("nuevaFechaHora no puede ser nula");
        }

        buscarCitaParaSolicitud(pacienteId, citaId);
        return crearYGuardarSolicitud(pacienteId, citaId, TipoSolicitudCita.CAMBIO_FECHA, nuevaFechaHora);
    }

    public SolicitudCita solicitarCancelacion(String pacienteId, String citaId) {
        validarIdentificador(pacienteId, "pacienteId");
        validarIdentificador(citaId, "citaId");

        buscarCitaParaSolicitud(pacienteId, citaId);
        return crearYGuardarSolicitud(pacienteId, citaId, TipoSolicitudCita.CANCELACION, null);
    }

    public List<SolicitudCita> consultarSolicitudes(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return repositorioSolicitudes.buscarPorPacienteId(pacienteId).stream()
                .filter(solicitud -> pacienteId.equals(solicitud.getPacienteId()))
                .toList();
    }

    private Cita buscarCitaParaSolicitud(String pacienteId, String citaId) {
        Cita cita = repositorio.buscarPorId(citaId)
                .orElseThrow(() -> new NoSuchElementException("Cita no encontrada"));
        if (!pacienteId.equals(cita.getPacienteId())) {
            throw new NoSuchElementException("Cita no encontrada");
        }
        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new IllegalStateException("No se pueden solicitar cambios para una cita cancelada");
        }
        return cita;
    }

    private SolicitudCita crearYGuardarSolicitud(
            String pacienteId, String citaId, TipoSolicitudCita tipo, LocalDateTime nuevaFechaHora) {
        SolicitudCita solicitud = new SolicitudCita(
                UUID.randomUUID().toString(), citaId, pacienteId, tipo, nuevaFechaHora);
        repositorioSolicitudes.guardar(solicitud);
        return solicitud;
    }

    private void validarIdentificador(String identificador, String nombre) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
