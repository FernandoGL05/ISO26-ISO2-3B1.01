package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;

public final class PruebaRealizada {
    private final String id;
    private final String pacienteId;
    private final String tipoPrueba;
    private final LocalDateTime fechaHoraRealizacion;
    private final String solicitudId;
    private final String citaId;

    public PruebaRealizada(
            String id,
            String pacienteId,
            String tipoPrueba,
            LocalDateTime fechaHoraRealizacion,
            String solicitudId) {
        this(id, pacienteId, tipoPrueba, fechaHoraRealizacion, solicitudId, null);
    }

    public PruebaRealizada(
            String id,
            String pacienteId,
            String tipoPrueba,
            LocalDateTime fechaHoraRealizacion,
            String solicitudId,
            String citaId) {
        validarObligatorio(id, "id");
        validarObligatorio(pacienteId, "pacienteId");
        validarObligatorio(tipoPrueba, "tipoPrueba");
        if (fechaHoraRealizacion == null) {
            throw new IllegalArgumentException("fechaHoraRealizacion no puede ser nula");
        }
        if (solicitudId != null && solicitudId.isBlank()) {
            throw new IllegalArgumentException("solicitudId no puede estar vacío");
        }
        if (citaId != null && citaId.isBlank()) {
            throw new IllegalArgumentException("citaId no puede estar vacío");
        }

        this.id = id;
        this.pacienteId = pacienteId;
        this.tipoPrueba = tipoPrueba;
        this.fechaHoraRealizacion = fechaHoraRealizacion;
        this.solicitudId = solicitudId;
        this.citaId = citaId;
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

    public LocalDateTime getFechaHoraRealizacion() {
        return fechaHoraRealizacion;
    }

    public String getSolicitudId() {
        return solicitudId;
    }

    public String getCitaId() {
        return citaId;
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
