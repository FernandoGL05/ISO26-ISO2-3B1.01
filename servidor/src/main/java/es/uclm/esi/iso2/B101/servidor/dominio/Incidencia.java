package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;

public final class Incidencia {
    public static final int MAX_LONGITUD_ASUNTO = 120;
    public static final int MAX_LONGITUD_DESCRIPCION = 2000;

    private final String id;
    private final String pacienteId;
    private final String asunto;
    private final String descripcion;
    private final String citaId;
    private final LocalDateTime fechaHoraCreacion;
    private final EstadoIncidencia estado;

    public Incidencia(
            String id,
            String pacienteId,
            String asunto,
            String descripcion,
            String citaId,
            LocalDateTime fechaHoraCreacion) {
        validarObligatorio(id, "id");
        validarObligatorio(pacienteId, "pacienteId");
        validarObligatorio(asunto, "asunto");
        validarObligatorio(descripcion, "descripcion");
        if (asunto.length() > MAX_LONGITUD_ASUNTO) {
            throw new IllegalArgumentException("asunto supera la longitud máxima permitida");
        }
        if (descripcion.length() > MAX_LONGITUD_DESCRIPCION) {
            throw new IllegalArgumentException("descripcion supera la longitud máxima permitida");
        }
        if (citaId != null && citaId.isBlank()) {
            throw new IllegalArgumentException("citaId no puede estar vacío");
        }
        if (fechaHoraCreacion == null) {
            throw new IllegalArgumentException("fechaHoraCreacion no puede ser nula");
        }

        this.id = id;
        this.pacienteId = pacienteId;
        this.asunto = asunto;
        this.descripcion = descripcion;
        this.citaId = citaId;
        this.fechaHoraCreacion = fechaHoraCreacion;
        this.estado = EstadoIncidencia.RECIBIDA;
    }

    public String getId() {
        return id;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCitaId() {
        return citaId;
    }

    public LocalDateTime getFechaHoraCreacion() {
        return fechaHoraCreacion;
    }

    public EstadoIncidencia getEstado() {
        return estado;
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
