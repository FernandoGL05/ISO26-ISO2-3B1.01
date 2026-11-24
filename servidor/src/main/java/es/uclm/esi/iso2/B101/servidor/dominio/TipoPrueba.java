package es.uclm.esi.iso2.B101.servidor.dominio;

import java.util.Objects;

/**
 * Representa un tipo de prueba diagnóstica configurable en el catálogo del sistema (CU20 / RF20).
 */
public final class TipoPrueba {

    private final String id;
    private final String nombre;
    private final String modalidad;
    private final String equipoRequerido;
    private final String salaRequerida;
    private final int duracionEstimadaMinutos;
    private final String preparacionPrevia;
    private final boolean requiereContraste;
    private final boolean requiereConsentimientoInformado;
    private final String requisitosSeguridad;
    private final PrioridadPrueba prioridad;
    private final boolean admiteSeguimientoPeriodico;
    private boolean activo;

    public TipoPrueba(
            String id,
            String nombre,
            String modalidad,
            String equipoRequerido,
            String salaRequerida,
            int duracionEstimadaMinutos,
            String preparacionPrevia,
            boolean requiereContraste,
            boolean requiereConsentimientoInformado,
            String requisitosSeguridad,
            PrioridadPrueba prioridad,
            boolean admiteSeguimientoPeriodico,
            boolean activo) {
        this.id = validarTexto(id, "id");
        this.nombre = validarTexto(nombre, "nombre");
        this.modalidad = validarTexto(modalidad, "modalidad");
        this.equipoRequerido = validarTexto(equipoRequerido, "equipoRequerido");
        this.salaRequerida = validarTexto(salaRequerida, "salaRequerida");
        if (duracionEstimadaMinutos <= 0 || duracionEstimadaMinutos > 24 * 60) {
            throw new IllegalArgumentException("duracionEstimadaMinutos debe estar entre 1 y 1440");
        }
        this.duracionEstimadaMinutos = duracionEstimadaMinutos;
        this.preparacionPrevia = validarTexto(preparacionPrevia, "preparacionPrevia");
        if (requiereContraste && !requiereConsentimientoInformado) {
            throw new IllegalArgumentException(
                    "Si la prueba requiere contraste debe requerir consentimiento informado");
        }
        this.requiereContraste = requiereContraste;
        this.requiereConsentimientoInformado = requiereConsentimientoInformado;
        this.requisitosSeguridad = validarTexto(requisitosSeguridad, "requisitosSeguridad");
        this.prioridad = Objects.requireNonNull(prioridad, "prioridad no puede ser nula");
        this.admiteSeguimientoPeriodico = admiteSeguimientoPeriodico;
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

    public String getNombre() {
        return nombre;
    }

    public String getModalidad() {
        return modalidad;
    }

    public String getEquipoRequerido() {
        return equipoRequerido;
    }

    public String getSalaRequerida() {
        return salaRequerida;
    }

    public int getDuracionEstimadaMinutos() {
        return duracionEstimadaMinutos;
    }

    public String getPreparacionPrevia() {
        return preparacionPrevia;
    }

    public boolean isRequiereContraste() {
        return requiereContraste;
    }

    public boolean isRequiereConsentimientoInformado() {
        return requiereConsentimientoInformado;
    }

    public String getRequisitosSeguridad() {
        return requisitosSeguridad;
    }

    public PrioridadPrueba getPrioridad() {
        return prioridad;
    }

    public boolean isAdmiteSeguimientoPeriodico() {
        return admiteSeguimientoPeriodico;
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

    public TipoPrueba conActualizacion(
            String nombre,
            String modalidad,
            String equipoRequerido,
            String salaRequerida,
            int duracionEstimadaMinutos,
            String preparacionPrevia,
            boolean requiereContraste,
            boolean requiereConsentimientoInformado,
            String requisitosSeguridad,
            PrioridadPrueba prioridad,
            boolean admiteSeguimientoPeriodico) {
        return new TipoPrueba(
                this.id,
                nombre,
                modalidad,
                equipoRequerido,
                salaRequerida,
                duracionEstimadaMinutos,
                preparacionPrevia,
                requiereContraste,
                requiereConsentimientoInformado,
                requisitosSeguridad,
                prioridad,
                admiteSeguimientoPeriodico,
                this.activo);
    }
}
