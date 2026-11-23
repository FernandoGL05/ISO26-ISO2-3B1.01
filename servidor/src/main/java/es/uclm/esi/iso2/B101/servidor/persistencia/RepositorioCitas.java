package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import java.util.List;
import java.util.Optional;

public interface RepositorioCitas {
    void guardar(Cita cita);

    Optional<Cita> buscarPorId(String id);

    List<Cita> buscarPorPacienteId(String pacienteId);

    List<Cita> buscarPorMedicoId(String medicoId);
}
