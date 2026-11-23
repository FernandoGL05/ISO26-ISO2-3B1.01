package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

public final class InformePrueba {
    private final String id;
    private final String pruebaId;
    private final String pacienteId;
    private final String medicoId;
    private final String hallazgos;
    private final String conclusion;
    private final LocalDateTime fechaCreacion;
    private final String tipoImagen;
    private final byte[] imagen;

    public InformePrueba(
            String id,
            String pruebaId,
            String pacienteId,
            String medicoId,
            String hallazgos,
            String conclusion,
            LocalDateTime fechaCreacion,
            String tipoImagen,
            byte[] imagen) {
        validarObligatorio(id, "id");
        validarObligatorio(pruebaId, "pruebaId");
        validarObligatorio(pacienteId, "pacienteId");
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(hallazgos, "hallazgos");
        validarObligatorio(conclusion, "conclusion");
        Objects.requireNonNull(fechaCreacion, "fechaCreacion no puede ser nula");
        if ((tipoImagen == null) != (imagen == null)) {
            throw new IllegalArgumentException("tipoImagen e imagen deben proporcionarse conjuntamente");
        }
        if (tipoImagen != null && tipoImagen.isBlank()) {
            throw new IllegalArgumentException("tipoImagen no puede estar vacío");
        }
        this.id = id;
        this.pruebaId = pruebaId;
        this.pacienteId = pacienteId;
        this.medicoId = medicoId;
        this.hallazgos = hallazgos;
        this.conclusion = conclusion;
        this.fechaCreacion = fechaCreacion;
        this.tipoImagen = tipoImagen;
        this.imagen = imagen == null ? null : imagen.clone();
    }

    public String getId() {
        return id;
    }

    public String getPruebaId() {
        return pruebaId;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public String getMedicoId() {
        return medicoId;
    }

    public String getHallazgos() {
        return hallazgos;
    }

    public String getConclusion() {
        return conclusion;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public String getTipoImagen() {
        return tipoImagen;
    }

    public byte[] getImagen() {
        return imagen == null ? null : imagen.clone();
    }

    public boolean tieneImagen() {
        return imagen != null;
    }

    public InformePrueba conImagen(String nuevoTipoImagen, byte[] nuevosBytes) {
        if (tieneImagen()) {
            throw new IllegalStateException("El informe ya tiene una imagen adjunta");
        }
        return new InformePrueba(
                id, pruebaId, pacienteId, medicoId, hallazgos, conclusion,
                fechaCreacion, nuevoTipoImagen, nuevosBytes);
    }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (!(objeto instanceof InformePrueba otro)) {
            return false;
        }
        return id.equals(otro.id)
                && pruebaId.equals(otro.pruebaId)
                && pacienteId.equals(otro.pacienteId)
                && medicoId.equals(otro.medicoId)
                && hallazgos.equals(otro.hallazgos)
                && conclusion.equals(otro.conclusion)
                && fechaCreacion.equals(otro.fechaCreacion)
                && Objects.equals(tipoImagen, otro.tipoImagen)
                && Arrays.equals(imagen, otro.imagen);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(id, pruebaId, pacienteId, medicoId, hallazgos, conclusion, fechaCreacion, tipoImagen)
                + Arrays.hashCode(imagen);
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
