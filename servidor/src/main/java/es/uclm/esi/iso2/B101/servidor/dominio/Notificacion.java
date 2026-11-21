package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;

public final class Notificacion {
    private final String id;
    private final String pacienteId;
    private final TipoNotificacion tipo;
    private final String mensaje;
    private final String referenciaId;
    private final LocalDateTime fechaHoraCreacion;
    private final LocalDateTime momentoAviso;
    private volatile boolean leida;

    public Notificacion(
            String id,
            String pacienteId,
            TipoNotificacion tipo,
            String mensaje,
            String referenciaId,
            LocalDateTime fechaHoraCreacion,
            LocalDateTime momentoAviso) {
        validarIdentificador(id, "id");
        validarIdentificador(pacienteId, "pacienteId");
        validarIdentificador(referenciaId, "referenciaId");
        if (tipo == null) {
            throw new IllegalArgumentException("tipo no puede ser nulo");
        }
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("mensaje no puede ser nulo ni estar vacío");
        }
        if (fechaHoraCreacion == null) {
            throw new IllegalArgumentException("fechaHoraCreacion no puede ser nula");
        }
        if (tipo == TipoNotificacion.RECORDATORIO_CITA && momentoAviso == null) {
            throw new IllegalArgumentException("momentoAviso es obligatorio para un recordatorio");
        }
        if (tipo != TipoNotificacion.RECORDATORIO_CITA && momentoAviso != null) {
            throw new IllegalArgumentException("momentoAviso solo corresponde a recordatorios");
        }

        this.id = id;
        this.pacienteId = pacienteId;
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.referenciaId = referenciaId;
        this.fechaHoraCreacion = fechaHoraCreacion;
        this.momentoAviso = momentoAviso;
    }

    public String getId() {
        return id;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getReferenciaId() {
        return referenciaId;
    }

    public LocalDateTime getFechaHoraCreacion() {
        return fechaHoraCreacion;
    }

    public LocalDateTime getMomentoAviso() {
        return momentoAviso;
    }

    public boolean estaLeida() {
        return leida;
    }

    public void marcarComoLeida() {
        leida = true;
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
