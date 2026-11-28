package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.Protocolo;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioProtocolos;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Servicio de aplicación para la configuración de protocolos clínicos/técnicos (CU21 / RF21).
 */
public final class ServicioProtocolos {

    private final RepositorioProtocolos repositorioProtocolos;
    private final Predicate<String> existeTipoPrueba;
    private final AtomicLong secuenciaId = new AtomicLong(1);

    public ServicioProtocolos(RepositorioProtocolos repositorioProtocolos) {
        this(repositorioProtocolos, id -> id != null && !id.isBlank());
    }

    public ServicioProtocolos(
            RepositorioProtocolos repositorioProtocolos,
            Predicate<String> existeTipoPrueba) {
        this.repositorioProtocolos = Objects.requireNonNull(
                repositorioProtocolos, "repositorioProtocolos no puede ser nulo");
        this.existeTipoPrueba = Objects.requireNonNull(
                existeTipoPrueba, "existeTipoPrueba no puede ser nulo");
    }

    public Protocolo crearProtocolo(
            String codigo,
            String nombre,
            String tipoPruebaId,
            String descripcion,
            String pasosPreparacion,
            String requisitosSeguridad,
            boolean usoContraste) {
        validarTipoPruebaExiste(tipoPruebaId);
        validarCodigoUnico(codigo, null);
        String id = "PROT-" + secuenciaId.getAndIncrement();
        Protocolo protocolo = new Protocolo(
                id,
                codigo,
                nombre,
                tipoPruebaId,
                descripcion,
                pasosPreparacion,
                requisitosSeguridad,
                usoContraste,
                1,
                true);
        repositorioProtocolos.guardar(protocolo);
        return protocolo;
    }

    public Protocolo actualizarProtocolo(
            String id,
            String codigo,
            String nombre,
            String tipoPruebaId,
            String descripcion,
            String pasosPreparacion,
            String requisitosSeguridad,
            boolean usoContraste) {
        Protocolo existente = obtenerPorId(id);
        validarTipoPruebaExiste(tipoPruebaId);
        validarCodigoUnico(codigo, existente.getId());
        Protocolo actualizado = existente.conNuevaVersion(
                codigo,
                nombre,
                tipoPruebaId,
                descripcion,
                pasosPreparacion,
                requisitosSeguridad,
                usoContraste);
        repositorioProtocolos.guardar(actualizado);
        return actualizado;
    }

    public Protocolo cambiarEstado(String id, boolean activo) {
        Protocolo existente = obtenerPorId(id);
        if (activo) {
            existente.activar();
        } else {
            existente.desactivar();
        }
        repositorioProtocolos.guardar(existente);
        return existente;
    }

    public Protocolo obtenerPorId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser nulo ni estar vacío");
        }
        return repositorioProtocolos.buscarPorId(id)
                .orElseThrow(() -> new NoSuchElementException("Protocolo no encontrado: " + id));
    }

    public List<Protocolo> listarTodos() {
        return repositorioProtocolos.listarTodos();
    }

    public List<Protocolo> listarPorTipoPrueba(String tipoPruebaId) {
        if (tipoPruebaId == null || tipoPruebaId.isBlank()) {
            throw new IllegalArgumentException("tipoPruebaId no puede ser nulo ni estar vacío");
        }
        return repositorioProtocolos.buscarPorTipoPruebaId(tipoPruebaId);
    }

    private void validarTipoPruebaExiste(String tipoPruebaId) {
        if (tipoPruebaId == null || tipoPruebaId.isBlank()) {
            throw new IllegalArgumentException("tipoPruebaId no puede ser nulo ni estar vacío");
        }
        if (!existeTipoPrueba.test(tipoPruebaId.trim())) {
            throw new NoSuchElementException("No existe el tipo de prueba asociado: " + tipoPruebaId);
        }
    }

    private void validarCodigoUnico(String codigo, String idExcluido) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("codigo no puede ser nulo ni estar vacío");
        }
        repositorioProtocolos.buscarPorCodigo(codigo)
                .filter(existente -> !existente.getId().equals(idExcluido))
                .ifPresent(existente -> {
                    throw new IllegalStateException("Ya existe un protocolo con el código: " + codigo.trim());
                });
    }
}
