package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.TipoPrueba;
import java.util.List;
import java.util.Optional;

public interface RepositorioTiposPrueba {

    void guardar(TipoPrueba tipoPrueba);

    Optional<TipoPrueba> buscarPorId(String id);

    Optional<TipoPrueba> buscarPorNombre(String nombre);

    List<TipoPrueba> listarTodos();

    List<TipoPrueba> listarActivos();
}
