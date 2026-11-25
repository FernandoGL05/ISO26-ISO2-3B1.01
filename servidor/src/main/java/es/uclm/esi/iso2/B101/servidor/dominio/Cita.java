package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;
import java.util.OptionalInt;

public class Cita {
    private final String id;
    private final String pacienteId;
    private final String medicoId;
    private final String tipoPrueba;
    private final LocalDateTime fechaHora;
    private final Integer duracionMinutos;
    private EstadoCita estado;

    public Cita(
            String id,
            String pacienteId,
            String tipoPrueba,
            LocalDateTime fechaHora) {
        this(id, pacienteId, null, tipoPrueba, fechaHora);
    }

    public Cita(
            String id,
            String pacienteId,
            String medicoId,
            String tipoPrueba,
            LocalDateTime fechaHora) {
        this(id, pacienteId, medicoId, tipoPrueba, fechaHora, (Integer) null);
    }

    public Cita(
            String id,
            String pacienteId,
            String medicoId,
            String tipoPrueba,
            LocalDateTime fechaHora,
            int duracionMinutos) {
        this(id, pacienteId, medicoId, tipoPrueba, fechaHora,
                Integer.valueOf(duracionMinutos));
    }

    private Cita(
            String id,
            String pacienteId,
            String medicoId,
            String tipoPrueba,
            LocalDateTime fechaHora,
            Integer duracionMinutos) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "id no puede ser nulo ni estar vacio");
        }
        if (pacienteId == null || pacienteId.isBlank()) {
            throw new IllegalArgumentException(
                    "pacienteId no puede ser nulo ni estar vacio");
        }
        if (medicoId != null && medicoId.isBlank()) {
            throw new IllegalArgumentException(
                    "medicoId no puede estar vacio");
        }
        if (tipoPrueba == null || tipoPrueba.isBlank()) {
            throw new IllegalArgumentException(
                    "tipoPrueba no puede ser nulo ni estar vacio");
        }
        if (fechaHora == null) {
            throw new IllegalArgumentException(
                    "fechaHora no puede ser nula");
        }
        if (duracionMinutos != null
                && (duracionMinutos <= 0 || duracionMinutos > 1440)) {
            throw new IllegalArgumentException(
                    "duracionMinutos debe estar entre 1 y 1440");
        }

        this.id = id;
        this.pacienteId = pacienteId;
        this.medicoId = medicoId;
        this.tipoPrueba = tipoPrueba;
        this.fechaHora = fechaHora;
        this.duracionMinutos = duracionMinutos;
        this.estado = EstadoCita.PROGRAMADA;
    }

    public String getId() {
        return id;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public String getMedicoId() {
        return medicoId;
    }

    public String getTipoPrueba() {
        return tipoPrueba;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public OptionalInt getDuracionMinutos() {
        return duracionMinutos == null
                ? OptionalInt.empty()
                : OptionalInt.of(duracionMinutos);
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public void confirmar() {
        if (estado == EstadoCita.CANCELADA) {
            throw new IllegalStateException(
                    "No se puede confirmar una cita cancelada");
        }
        if (estado == EstadoCita.REALIZADA) {
            throw new IllegalStateException(
                    "No se puede confirmar una cita ya realizada");
        }

        estado = EstadoCita.CONFIRMADA;
    }

    public void cancelar() {
        if (estado == EstadoCita.REALIZADA) {
            throw new IllegalStateException(
                    "No se puede cancelar una cita ya realizada");
        }

        estado = EstadoCita.CANCELADA;
    }

    public void marcarRealizada() {
        if (estado == EstadoCita.CANCELADA) {
            throw new IllegalStateException(
                    "No se puede registrar la realizacion de una cita cancelada");
        }
        if (estado == EstadoCita.REALIZADA) {
            throw new IllegalStateException(
                    "La cita ya esta registrada como realizada");
        }

        estado = EstadoCita.REALIZADA;
    }
}