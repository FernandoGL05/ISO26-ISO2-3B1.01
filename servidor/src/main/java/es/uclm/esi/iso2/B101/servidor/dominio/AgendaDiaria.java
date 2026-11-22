package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDate;

public final class AgendaDiaria {
    private final String id;
    private final String medicoId;
    private final LocalDate fecha;

    public AgendaDiaria(String id, String medicoId, LocalDate fecha) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "id no puede ser nulo ni estar vacío");
        }
        if (medicoId == null || medicoId.isBlank()) {
            throw new IllegalArgumentException(
                    "medicoId no puede ser nulo ni estar vacío");
        }
        if (fecha == null) {
            throw new IllegalArgumentException(
                    "fecha no puede ser nula");
        }

        this.id = id;
        this.medicoId = medicoId;
        this.fecha = fecha;
    }

    public String getId() {
        return id;
    }

    public String getMedicoId() {
        return medicoId;
    }

    public LocalDate getFecha() {
        return fecha;
    }
}
