package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudPruebaDiagnostica;
import java.util.List;
import java.util.Optional;

public interface RepositorioPrescripcionesDiagnosticas {
    void guardar(SolicitudPruebaDiagnostica solicitud);

    Optional<SolicitudPruebaDiagnostica> buscarPorId(String id);

    List<SolicitudPruebaDiagnostica> buscarPorMedicoId(String medicoId);
}
