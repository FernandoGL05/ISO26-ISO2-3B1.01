package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;

public class SolicitudCita {
    private final String id;
    private final String citaId;
    private final String pacienteId;
    private final TipoSolicitudCita tipo;
    private final LocalDateTime nuevaFechaHora;
    private final EstadoSolicitudCita estado;

    public SolicitudCita(String id, String citaId, String pacienteId, TipoSolicitudCita tipo,
            LocalDateTime nuevaFechaHora) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser nulo ni estar vacío");
        }
        if (citaId == null || citaId.isBlank()) {
            throw new IllegalArgumentException("citaId no puede ser nulo ni estar vacío");
        }
        if (pacienteId == null || pacienteId.isBlank()) {
            throw new IllegalArgumentException("pacienteId no puede ser nulo ni estar vacío");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("tipo no puede ser nulo");
        }
        if (tipo == TipoSolicitudCita.CAMBIO_FECHA && nuevaFechaHora == null) {
            throw new IllegalArgumentException("nuevaFechaHora es obligatoria para un cambio de fecha");
        }
        if (tipo == TipoSolicitudCita.CANCELACION && nuevaFechaHora != null) {
            throw new IllegalArgumentException("nuevaFechaHora debe ser nula para una cancelación");
        }

        this.id = id;
        this.citaId = citaId;
        this.pacienteId = pacienteId;
        this.tipo = tipo;
        this.nuevaFechaHora = nuevaFechaHora;
        this.estado = EstadoSolicitudCita.PENDIENTE;
    }

    public String getId() {
        return id;
    }

    public String getCitaId() {
        return citaId;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public TipoSolicitudCita getTipo() {
        return tipo;
    }

    public LocalDateTime getNuevaFechaHora() {
        return nuevaFechaHora;
    }

    public EstadoSolicitudCita getEstado() {
        return estado;
    }
}
