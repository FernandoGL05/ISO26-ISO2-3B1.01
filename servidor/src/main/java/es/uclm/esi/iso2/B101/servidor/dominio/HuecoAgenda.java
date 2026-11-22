package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalTime;

public final class HuecoAgenda {
    private final String id;
    private final String agendaId;
    private final LocalTime horaInicio;
    private final LocalTime horaFin;

    public HuecoAgenda(
            String id,
            String agendaId,
            LocalTime horaInicio,
            LocalTime horaFin) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "id no puede ser nulo ni estar vacío");
        }
        if (agendaId == null || agendaId.isBlank()) {
            throw new IllegalArgumentException(
                    "agendaId no puede ser nulo ni estar vacío");
        }
        if (horaInicio == null || horaFin == null) {
            throw new IllegalArgumentException(
                    "Las horas de inicio y fin no pueden ser nulas");
        }
        if (!horaInicio.isBefore(horaFin)) {
            throw new IllegalArgumentException(
                    "La hora de inicio debe ser anterior a la hora de fin");
        }

        this.id = id;
        this.agendaId = agendaId;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    public String getId() {
        return id;
    }

    public String getAgendaId() {
        return agendaId;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public boolean seSolapaCon(HuecoAgenda otro) {
        if (otro == null) {
            throw new IllegalArgumentException(
                    "El otro hueco no puede ser nulo");
        }

        return agendaId.equals(otro.agendaId)
                && horaInicio.isBefore(otro.horaFin)
                && otro.horaInicio.isBefore(horaFin);
    }
}