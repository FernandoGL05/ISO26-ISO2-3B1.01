package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDate;

public final class SeguimientoPaciente {
    private final String id;
    private final String pacienteId;
    private final String tipoPrueba;
    private final String descripcion;
    private final Integer periodicidadMeses;
    private final LocalDate fechaUltimaPrueba;
    /** Fecha prevista; no implica que exista una cita asignada. */
    private final LocalDate fechaProximaRevision;
    private final String observacionesPreparacion;

    public SeguimientoPaciente(
            String id,
            String pacienteId,
            String tipoPrueba,
            String descripcion,
            Integer periodicidadMeses,
            LocalDate fechaUltimaPrueba,
            LocalDate fechaProximaRevision,
            String observacionesPreparacion) {
        validarObligatorio(id, "id");
        validarObligatorio(pacienteId, "pacienteId");
        validarObligatorio(tipoPrueba, "tipoPrueba");
        validarObligatorio(descripcion, "descripcion");
        if (periodicidadMeses != null && periodicidadMeses <= 0) {
            throw new IllegalArgumentException("La periodicidad debe ser mayor que cero meses");
        }
        if (observacionesPreparacion != null && observacionesPreparacion.isBlank()) {
            throw new IllegalArgumentException("observacionesPreparacion no puede estar vacío");
        }

        this.id = id;
        this.pacienteId = pacienteId;
        this.tipoPrueba = tipoPrueba;
        this.descripcion = descripcion;
        this.periodicidadMeses = periodicidadMeses;
        this.fechaUltimaPrueba = fechaUltimaPrueba;
        this.fechaProximaRevision = fechaProximaRevision;
        this.observacionesPreparacion = observacionesPreparacion;
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

    public String getDescripcion() {
        return descripcion;
    }

    public Integer getPeriodicidadMeses() {
        return periodicidadMeses;
    }

    public LocalDate getFechaUltimaPrueba() {
        return fechaUltimaPrueba;
    }

    public LocalDate getFechaProximaRevision() {
        return fechaProximaRevision;
    }

    public String getObservacionesPreparacion() {
        return observacionesPreparacion;
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
