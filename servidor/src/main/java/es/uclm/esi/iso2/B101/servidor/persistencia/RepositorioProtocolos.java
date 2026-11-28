package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Protocolo;
import java.util.List;
import java.util.Optional;

public interface RepositorioProtocolos {

    void guardar(Protocolo protocolo);

    Optional<Protocolo> buscarPorId(String id);

    Optional<Protocolo> buscarPorCodigo(String codigo);

    List<Protocolo> buscarPorTipoPruebaId(String tipoPruebaId);

    List<Protocolo> listarTodos();
}
