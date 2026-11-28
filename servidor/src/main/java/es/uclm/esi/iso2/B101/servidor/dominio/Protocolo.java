package es.uclm.esi.iso2.B101.servidor.dominio;

/**
 * Representa un protocolo clínico/técnico asociado a un tipo de prueba (CU21 / RF21).
 */
public final class Protocolo {

    private final String id;
    private final String codigo;
    private final String nombre;
    private final String tipoPruebaId;
    private final String descripcion;
    private final String pasosPreparacion;
    private final String requisitosSeguridad;
    private final boolean usoContraste;
    private final int version;
    private boolean activo;

    public Protocolo(
            String id,
            String codigo,
            String nombre,
            String tipoPruebaId,
            String descripcion,
            String pasosPreparacion,
            String requisitosSeguridad,
            boolean usoContraste,
            int version,
            boolean activo) {
        this.id = validarTexto(id, "id");
        this.codigo = validarTexto(codigo, "codigo");
        this.nombre = validarTexto(nombre, "nombre");
        this.tipoPruebaId = validarTexto(tipoPruebaId, "tipoPruebaId");
        this.descripcion = validarTexto(descripcion, "descripcion");
        this.pasosPreparacion = validarTexto(pasosPreparacion, "pasosPreparacion");
        this.requisitosSeguridad = validarTexto(requisitosSeguridad, "requisitosSeguridad");
        if (version < 1) {
            throw new IllegalArgumentException("version debe ser mayor o igual que 1");
        }
        this.usoContraste = usoContraste;
        this.version = version;
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

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTipoPruebaId() {
        return tipoPruebaId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getPasosPreparacion() {
        return pasosPreparacion;
    }

    public String getRequisitosSeguridad() {
        return requisitosSeguridad;
    }

    public boolean isUsoContraste() {
        return usoContraste;
    }

    public int getVersion() {
        return version;
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

    public Protocolo conNuevaVersion(
            String codigo,
            String nombre,
            String tipoPruebaId,
            String descripcion,
            String pasosPreparacion,
            String requisitosSeguridad,
            boolean usoContraste) {
        return new Protocolo(
                this.id,
                codigo,
                nombre,
                tipoPruebaId,
                descripcion,
                pasosPreparacion,
                requisitosSeguridad,
                usoContraste,
                this.version + 1,
                this.activo);
    }
}
