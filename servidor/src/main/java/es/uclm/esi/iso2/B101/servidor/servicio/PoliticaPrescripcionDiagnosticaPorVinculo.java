package es.uclm.esi.iso2.B101.servidor.servicio;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PoliticaPrescripcionDiagnosticaPorVinculo implements PoliticaPrescripcionDiagnostica {
    private final Map<String, Set<String>> pacientesPorMedico;

    public PoliticaPrescripcionDiagnosticaPorVinculo(Map<String, Set<String>> pacientesPorMedico) {
        Objects.requireNonNull(pacientesPorMedico, "pacientesPorMedico no puede ser nulo");
        Map<String, Set<String>> asociacionesValidadas = new HashMap<>();
        for (Map.Entry<String, Set<String>> entrada : pacientesPorMedico.entrySet()) {
            String medicoId = entrada.getKey();
            Set<String> pacienteIds = entrada.getValue();
            if (medicoId == null || medicoId.isBlank() || pacienteIds == null) {
                throw new IllegalArgumentException("Cada médico debe tener una lista de pacientes válida");
            }
            if (pacienteIds.stream().anyMatch(pacienteId -> pacienteId == null || pacienteId.isBlank())) {
                throw new IllegalArgumentException("Los pacienteId asociados deben tener valor");
            }
            asociacionesValidadas.put(medicoId, Set.copyOf(pacienteIds));
        }
        this.pacientesPorMedico = Map.copyOf(asociacionesValidadas);
    }

    @Override
    public boolean puedePrescribir(String medicoId, String pacienteId) {
        return pacientesPorMedico.getOrDefault(medicoId, Set.of()).contains(pacienteId);
    }
}
