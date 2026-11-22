package es.uclm.esi.iso2.B101.servidor.dominio;

public final class EncuestaSatisfaccion {
    private final String id;
    private final String titulo;
    private final String descripcion;
    private final boolean disponible;

    public EncuestaSatisfaccion(String id, String titulo, String descripcion, boolean disponible) {
        validarObligatorio(id, "id");
        validarObligatorio(titulo, "titulo");
        validarObligatorio(descripcion, "descripcion");
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.disponible = disponible;
    }

    public String getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean estaDisponible() {
        return disponible;
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
