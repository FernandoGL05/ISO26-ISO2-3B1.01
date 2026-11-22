package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;

public final class SolicitudPruebaDiagnostica {
    private final String id;
    private final String medicoId;
    private final String pacienteId;
    private final String tipoPrueba;
    private final String indicacionClinica;
    private final String justificacion;
    private final PrioridadPrueba prioridad;
    private final LocalDateTime fecha;
    private final EstadoSolicitudPrueba estado;

    public SolicitudPruebaDiagnostica(
            String id,
            String medicoId,
            String pacienteId,
            String tipoPrueba,
            String indicacionClinica,
            String justificacion,
            PrioridadPrueba prioridad,
            LocalDateTime fecha) {
        validarObligatorio(id, "id");
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(pacienteId, "pacienteId");
        validarObligatorio(tipoPrueba, "tipoPrueba");
        validarObligatorio(indicacionClinica, "indicacionClinica");
        validarObligatorio(justificacion, "justificacion");
        if (prioridad == null) {
            throw new IllegalArgumentException("prioridad no puede ser nula");
        }
        if (fecha == null) {
            throw new IllegalArgumentException("fecha no puede ser nula");
        }
        this.prioridad = prioridad;
        this.fecha = fecha;
        this.id = id;
        this.medicoId = medicoId;
        this.pacienteId = pacienteId;
        this.tipoPrueba = tipoPrueba;
        this.indicacionClinica = indicacionClinica;
        this.justificacion = justificacion;
        this.estado = EstadoSolicitudPrueba.PENDIENTE;
    }

    public String getId() {
        return id;
    }

    public String getMedicoId() {
        return medicoId;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public String getTipoPrueba() {
        return tipoPrueba;
    }

    public String getIndicacionClinica() {
        return indicacionClinica;
    }

    public String getJustificacion() {
        return justificacion;
    }

    public PrioridadPrueba getPrioridad() {
        return prioridad;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public EstadoSolicitudPrueba getEstado() {
        return estado;
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
