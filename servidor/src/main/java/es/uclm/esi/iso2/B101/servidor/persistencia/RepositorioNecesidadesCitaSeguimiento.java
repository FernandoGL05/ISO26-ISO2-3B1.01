package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.NecesidadCitaSeguimiento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RepositorioNecesidadesCitaSeguimiento {
    boolean guardarSiNoExiste(NecesidadCitaSeguimiento necesidad);

    Optional<NecesidadCitaSeguimiento> buscarPorPrescripcionYFecha(String prescripcionId, LocalDate fechaPrevista);

    List<NecesidadCitaSeguimiento> buscarPorPrescripcionId(String prescripcionId);
}
