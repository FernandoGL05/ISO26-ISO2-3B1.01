package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;

public final class EntradaListaEspera {
    private final String id;
    private final String pacienteId;
    private final String medicoId;
    private final String tipoPrueba;
    private final PrioridadPrueba prioridad;
    private final LocalDateTime fechaEntrada;
    private final EstadoEntradaListaEspera estado;
    private final String citaId;

    public EntradaListaEspera(
            String id,
            String pacienteId,
            String medicoId,
            String tipoPrueba,
            PrioridadPrueba prioridad,
            LocalDateTime fechaEntrada) {
        this(id, pacienteId, medicoId, tipoPrueba, prioridad,
                fechaEntrada, EstadoEntradaListaEspera.PENDIENTE, null);
    }

    private EntradaListaEspera(
            String id,
            String pacienteId,
            String medicoId,
            String tipoPrueba,
            PrioridadPrueba prioridad,
            LocalDateTime fechaEntrada,
            EstadoEntradaListaEspera estado,
            String citaId) {
        validarTexto(id, "id");
        validarTexto(pacienteId, "pacienteId");
        validarTexto(medicoId, "medicoId");
        validarTexto(tipoPrueba, "tipoPrueba");

        if (prioridad == null) {
            throw new IllegalArgumentException("prioridad es obligatoria");
        }
        if (fechaEntrada == null) {
            throw new IllegalArgumentException("fechaEntrada es obligatoria");
        }

        this.id = id;
        this.pacienteId = pacienteId;
        this.medicoId = medicoId;
        this.tipoPrueba = tipoPrueba;
        this.prioridad = prioridad;
        this.fechaEntrada = fechaEntrada;
        this.estado = estado;
        this.citaId = citaId;
    }

    public EntradaListaEspera cambiarPrioridad(PrioridadPrueba nuevaPrioridad) {
        comprobarPendiente();
        return new EntradaListaEspera(
                id, pacienteId, medicoId, tipoPrueba, nuevaPrioridad,
                fechaEntrada, estado, citaId);
    }

    public EntradaListaEspera asignarCita(String nuevaCitaId) {
        comprobarPendiente();
        validarTexto(nuevaCitaId, "citaId");

        return new EntradaListaEspera(
                id, pacienteId, medicoId, tipoPrueba, prioridad,
                fechaEntrada, EstadoEntradaListaEspera.CITADA, nuevaCitaId);
    }

    public EntradaListaEspera cancelar() {
        comprobarPendiente();
        return new EntradaListaEspera(
                id, pacienteId, medicoId, tipoPrueba, prioridad,
                fechaEntrada, EstadoEntradaListaEspera.CANCELADA, null);
    }

    private void comprobarPendiente() {
        if (estado != EstadoEntradaListaEspera.PENDIENTE) {
            throw new IllegalStateException(
                    "Solo se pueden modificar entradas pendientes");
        }
    }

    private static void validarTexto(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " no puede ser nulo ni estar vacio");
        }
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

    public PrioridadPrueba getPrioridad() {
        return prioridad;
    }

    public LocalDateTime getFechaEntrada() {
        return fechaEntrada;
    }

    public EstadoEntradaListaEspera getEstado() {
        return estado;
    }

    public String getCitaId() {
        return citaId;
    }
}