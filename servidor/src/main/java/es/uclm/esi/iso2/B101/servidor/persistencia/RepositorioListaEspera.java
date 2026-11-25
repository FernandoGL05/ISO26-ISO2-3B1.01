package es.uclm.esi.iso2.B101.servidor.persistencia;

import java.util.List;
import java.util.Optional;

import es.uclm.esi.iso2.B101.servidor.dominio.EntradaListaEspera;

public interface RepositorioListaEspera {
    void guardar(EntradaListaEspera entrada);

    Optional<EntradaListaEspera> buscarPorId(String id);

    List<EntradaListaEspera> buscarPendientes();

    List<EntradaListaEspera> buscarPorPacienteId(String pacienteId);
}