package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;

public class Cita {
    private final String id;
    private final String pacienteId;
    private final String tipoPrueba;
    private final LocalDateTime fechaHora;
    private EstadoCita estado;

    public Cita(String id, String pacienteId, String tipoPrueba, LocalDateTime fechaHora) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser nulo ni estar vacío");
        }
        if (pacienteId == null || pacienteId.isBlank()) {
            throw new IllegalArgumentException("pacienteId no puede ser nulo ni estar vacío");
        }
        if (tipoPrueba == null || tipoPrueba.isBlank()) {
            throw new IllegalArgumentException("tipoPrueba no puede ser nulo ni estar vacío");
        }
        if (fechaHora == null) {
            throw new IllegalArgumentException("fechaHora no puede ser nula");
        }

        this.id = id;
        this.pacienteId = pacienteId;
        this.tipoPrueba = tipoPrueba;
        this.fechaHora = fechaHora;
        this.estado = EstadoCita.PROGRAMADA;
    }

    public String getId() {
        return id;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public String getTipoPrueba() {
        return tipoPrueba;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public void confirmar() {
        if (estado == EstadoCita.CANCELADA) {
            throw new IllegalStateException("No se puede confirmar una cita cancelada");
        }
        estado = EstadoCita.CONFIRMADA;
    }
}
