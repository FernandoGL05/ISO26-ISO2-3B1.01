package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDate;

public final class PrescripcionSeguimiento {
    private final String id;
    private final String medicoId;
    private final String pacienteId;
    private final String tipoPrueba;
    private final String motivoClinico;
    private final int periodicidadMeses;
    private final LocalDate fechaPrimeraRevision;
    private final PrioridadSeguimiento prioridad;
    private final LocalDate fechaCreacion;
    private final EstadoPrescripcionSeguimiento estado;

    public PrescripcionSeguimiento(
            String id,
            String medicoId,
            String pacienteId,
            String tipoPrueba,
            String motivoClinico,
            int periodicidadMeses,
            LocalDate fechaPrimeraRevision,
            PrioridadSeguimiento prioridad,
            LocalDate fechaCreacion) {
        validarObligatorio(id, "id");
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(pacienteId, "pacienteId");
        validarObligatorio(tipoPrueba, "tipoPrueba");
        validarObligatorio(motivoClinico, "motivoClinico");
        if (periodicidadMeses <= 0) {
            throw new IllegalArgumentException("periodicidadMeses debe ser positiva");
        }
        if (fechaPrimeraRevision == null) {
            throw new IllegalArgumentException("fechaPrimeraRevision no puede ser nula");
        }
        if (prioridad == null) {
            throw new IllegalArgumentException("prioridad no puede ser nula");
        }
        if (fechaCreacion == null) {
            throw new IllegalArgumentException("fechaCreacion no puede ser nula");
        }
        this.id = id;
        this.medicoId = medicoId;
        this.pacienteId = pacienteId;
        this.tipoPrueba = tipoPrueba;
        this.motivoClinico = motivoClinico;
        this.periodicidadMeses = periodicidadMeses;
        this.fechaPrimeraRevision = fechaPrimeraRevision;
        this.prioridad = prioridad;
        this.fechaCreacion = fechaCreacion;
        this.estado = EstadoPrescripcionSeguimiento.ACTIVA;
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

    public String getMotivoClinico() {
        return motivoClinico;
    }

    public int getPeriodicidadMeses() {
        return periodicidadMeses;
    }

    public LocalDate getFechaPrimeraRevision() {
        return fechaPrimeraRevision;
    }

    public PrioridadSeguimiento getPrioridad() {
        return prioridad;
    }

    public LocalDate getFechaCreacion() {
        return fechaCreacion;
    }

    public EstadoPrescripcionSeguimiento getEstado() {
        return estado;
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
