package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import java.util.List;
import java.util.Optional;

public interface RepositorioSeguimientos {
    void registrar(SeguimientoPaciente seguimiento);

    Optional<SeguimientoPaciente> buscarPorId(String id);

    List<SeguimientoPaciente> buscarPorPacienteId(String pacienteId);
}
