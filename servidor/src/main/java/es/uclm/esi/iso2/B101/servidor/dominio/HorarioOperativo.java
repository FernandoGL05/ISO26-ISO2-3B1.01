package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Objects;

/**
 * Representa una franja horaria operativa configurada para una sala, equipo o médico (CU22 / RF22).
 */
public final class HorarioOperativo {

    private final String id;
    private final String recursoId;
    private final TipoRecursoHorario tipoRecurso;
    private final DayOfWeek diaSemana;
    private final LocalTime horaInicio;
    private final LocalTime horaFin;
    private final int duracionHuecoMinutos;
    private boolean activo;

    public HorarioOperativo(
            String id,
            String recursoId,
            TipoRecursoHorario tipoRecurso,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            int duracionHuecoMinutos,
            boolean activo) {
        this.id = validarTexto(id, "id");
        this.recursoId = validarTexto(recursoId, "recursoId");
        this.tipoRecurso = Objects.requireNonNull(tipoRecurso, "tipoRecurso no puede ser nulo");
        this.diaSemana = Objects.requireNonNull(diaSemana, "diaSemana no puede ser nulo");
        this.horaInicio = Objects.requireNonNull(horaInicio, "horaInicio no puede ser nula");
        this.horaFin = Objects.requireNonNull(horaFin, "horaFin no puede ser nula");
        if (!horaInicio.isBefore(horaFin)) {
            throw new IllegalArgumentException("horaInicio debe ser estrictamente anterior a horaFin");
        }
        if (duracionHuecoMinutos <= 0 || duracionHuecoMinutos > 24 * 60) {
            throw new IllegalArgumentException("duracionHuecoMinutos debe estar entre 1 y 1440");
        }
        this.duracionHuecoMinutos = duracionHuecoMinutos;
        this.activo = activo;
    }

    private static String validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede ser nulo ni estar vacío");
        }
        return valor.trim();
    }

    public String getId() {
        return id;
    }

    public String getRecursoId() {
        return recursoId;
    }

    public TipoRecursoHorario getTipoRecurso() {
        return tipoRecurso;
    }

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public int getDuracionHuecoMinutos() {
        return duracionHuecoMinutos;
    }

    public boolean isActivo() {
        return activo;
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    public boolean seSolapaCon(HorarioOperativo otro) {
        if (otro == null) {
            return false;
        }
        boolean mismoRecurso = this.recursoId.toLowerCase(Locale.ROOT)
                .equals(otro.recursoId.toLowerCase(Locale.ROOT));
        return mismoRecurso
                && this.diaSemana == otro.diaSemana
                && this.horaInicio.isBefore(otro.horaFin)
                && otro.horaInicio.isBefore(this.horaFin);
    }

    public HorarioOperativo conActualizacion(
            String recursoId,
            TipoRecursoHorario tipoRecurso,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            int duracionHuecoMinutos) {
        return new HorarioOperativo(
                this.id,
                recursoId,
                tipoRecurso,
                diaSemana,
                horaInicio,
                horaFin,
                duracionHuecoMinutos,
                this.activo);
    }
}
