package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import java.util.List;
import java.util.Optional;

public interface RepositorioHistorialPruebas {
    void registrar(PruebaRealizada prueba);

    Optional<PruebaRealizada> buscarPorId(String id);

    List<PruebaRealizada> buscarPorPacienteId(String pacienteId);
}
