package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;

public final class RespuestaEncuestaSatisfaccion {
    public static final int LONGITUD_MAXIMA_COMENTARIO = 2000;

    private final String id;
    private final String encuestaId;
    private final String pacienteId;
    private final int puntuacion;
    private final String comentario;
    private final LocalDateTime fechaRespuesta;

    public RespuestaEncuestaSatisfaccion(
            String id,
            String encuestaId,
            String pacienteId,
            int puntuacion,
            String comentario,
            LocalDateTime fechaRespuesta) {
        validarObligatorio(id, "id");
        validarObligatorio(encuestaId, "encuestaId");
        validarObligatorio(pacienteId, "pacienteId");
        if (puntuacion < 1 || puntuacion > 5) {
            throw new IllegalArgumentException("La puntuación debe estar entre 1 y 5");
        }
        if (comentario != null && comentario.length() > LONGITUD_MAXIMA_COMENTARIO) {
            throw new IllegalArgumentException(
                    "El comentario no puede superar " + LONGITUD_MAXIMA_COMENTARIO + " caracteres");
        }
        if (fechaRespuesta == null) {
            throw new IllegalArgumentException("fechaRespuesta no puede ser nula");
        }
        this.id = id;
        this.encuestaId = encuestaId;
        this.pacienteId = pacienteId;
        this.puntuacion = puntuacion;
        this.comentario = comentario;
        this.fechaRespuesta = fechaRespuesta;
    }

    public String getId() {
        return id;
    }

    public String getEncuestaId() {
        return encuestaId;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public int getPuntuacion() {
        return puntuacion;
    }

    public String getComentario() {
        return comentario;
    }

    public LocalDateTime getFechaRespuesta() {
        return fechaRespuesta;
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
