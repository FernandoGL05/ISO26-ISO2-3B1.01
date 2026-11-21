package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Incidencia;
import java.util.List;
import java.util.Optional;

public interface RepositorioIncidencias {
    void guardar(Incidencia incidencia);

    Optional<Incidencia> buscarPorId(String id);

    List<Incidencia> buscarPorPacienteId(String pacienteId);
}
