package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.PrescripcionSeguimiento;
import java.util.List;
import java.util.Optional;

public interface RepositorioPrescripcionesSeguimiento {
    void guardar(PrescripcionSeguimiento prescripcion);

    Optional<PrescripcionSeguimiento> buscarPorId(String id);

    List<PrescripcionSeguimiento> buscarPorMedicoId(String medicoId);

    List<PrescripcionSeguimiento> buscarTodas();
}
