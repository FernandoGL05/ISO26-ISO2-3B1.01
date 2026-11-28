package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoPrueba;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioTiposPrueba;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de aplicación para la configuración del catálogo de tipos de prueba (CU20 / RF20).
 */
public final class ServicioTiposPrueba {

    private final RepositorioTiposPrueba repositorioTiposPrueba;
    private final AtomicLong secuenciaId = new AtomicLong(1);

    public ServicioTiposPrueba(RepositorioTiposPrueba repositorioTiposPrueba) {
        this.repositorioTiposPrueba = Objects.requireNonNull(
                repositorioTiposPrueba, "repositorioTiposPrueba no puede ser nulo");
    }

    public TipoPrueba crearTipoPrueba(
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
        validarNombreUnico(nombre, null);
        String id = "TP-" + secuenciaId.getAndIncrement();
        TipoPrueba tipoPrueba = new TipoPrueba(
                id,
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
                true);
        repositorioTiposPrueba.guardar(tipoPrueba);
        return tipoPrueba;
    }

    public TipoPrueba actualizarTipoPrueba(
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
            boolean admiteSeguimientoPeriodico) {
        TipoPrueba existente = obtenerPorId(id);
        validarNombreUnico(nombre, existente.getId());
        TipoPrueba actualizado = existente.conActualizacion(
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
                admiteSeguimientoPeriodico);
        repositorioTiposPrueba.guardar(actualizado);
        return actualizado;
    }

    public TipoPrueba cambiarEstado(String id, boolean activo) {
        TipoPrueba existente = obtenerPorId(id);
        if (activo) {
            existente.activar();
        } else {
            existente.desactivar();
        }
        repositorioTiposPrueba.guardar(existente);
        return existente;
    }

    public TipoPrueba obtenerPorId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser nulo ni estar vacío");
        }
        return repositorioTiposPrueba.buscarPorId(id)
                .orElseThrow(() -> new NoSuchElementException("Tipo de prueba no encontrado: " + id));
    }

    public List<TipoPrueba> listarTodos() {
        return repositorioTiposPrueba.listarTodos();
    }

    public List<TipoPrueba> listarActivos() {
        return repositorioTiposPrueba.listarActivos();
    }

    private void validarNombreUnico(String nombre, String idExcluido) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("nombre no puede ser nulo ni estar vacío");
        }
        repositorioTiposPrueba.buscarPorNombre(nombre)
                .filter(existente -> !existente.getId().equals(idExcluido))
                .ifPresent(existente -> {
                    throw new IllegalStateException("Ya existe un tipo de prueba con el nombre: " + nombre.trim());
                });
    }
}
