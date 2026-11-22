package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDate;

public final class NecesidadCitaSeguimiento {
    private final String id;
    private final String prescripcionId;
    private final String medicoId;
    private final String pacienteId;
    private final String tipoPrueba;
    private final LocalDate fechaPrevista;
    private final EstadoNecesidadCitaSeguimiento estado;
    private final String citaId;

    public NecesidadCitaSeguimiento(
            String id,
            String prescripcionId,
            String medicoId,
            String pacienteId,
            String tipoPrueba,
            LocalDate fechaPrevista) {
        validarObligatorio(id, "id");
        validarObligatorio(prescripcionId, "prescripcionId");
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(pacienteId, "pacienteId");
        validarObligatorio(tipoPrueba, "tipoPrueba");
        if (fechaPrevista == null) {
            throw new IllegalArgumentException("fechaPrevista no puede ser nula");
        }
        this.id = id;
        this.prescripcionId = prescripcionId;
        this.medicoId = medicoId;
        this.pacienteId = pacienteId;
        this.tipoPrueba = tipoPrueba;
        this.fechaPrevista = fechaPrevista;
        this.estado = EstadoNecesidadCitaSeguimiento.PENDIENTE_PROGRAMACION;
        this.citaId = null;
    }

    public String getId() {
        return id;
    }

    public String getPrescripcionId() {
        return prescripcionId;
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

    public LocalDate getFechaPrevista() {
        return fechaPrevista;
    }

    public EstadoNecesidadCitaSeguimiento getEstado() {
        return estado;
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
