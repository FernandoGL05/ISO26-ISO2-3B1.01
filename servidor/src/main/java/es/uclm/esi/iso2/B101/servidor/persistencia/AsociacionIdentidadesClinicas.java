package es.uclm.esi.iso2.B101.servidor.persistencia;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AsociacionIdentidadesClinicas {
    private final Map<String, String> pacientesPorUsuarioId;
    private final Map<String, String> medicosPorUsuarioId;

    public AsociacionIdentidadesClinicas(
            Map<String, String> pacientesPorUsuarioId,
            Map<String, String> medicosPorUsuarioId) {
        this.pacientesPorUsuarioId = validarAsociaciones(pacientesPorUsuarioId, "pacienteId");
        this.medicosPorUsuarioId = validarAsociaciones(medicosPorUsuarioId, "medicoId");
    }

    public Optional<String> pacienteIdDe(String usuarioId) {
        return Optional.ofNullable(pacientesPorUsuarioId.get(usuarioId));
    }

    public Optional<String> medicoIdDe(String usuarioId) {
        return Optional.ofNullable(medicosPorUsuarioId.get(usuarioId));
    }

    private static Map<String, String> validarAsociaciones(Map<String, String> asociaciones, String nombreId) {
        Objects.requireNonNull(asociaciones, "asociaciones no pueden ser nulas");
        asociaciones.forEach((usuarioId, dominioId) -> {
            if (usuarioId == null || usuarioId.isBlank() || dominioId == null || dominioId.isBlank()) {
                throw new IllegalArgumentException("usuarioId y " + nombreId + " deben tener valor");
            }
        });
        return Map.copyOf(asociaciones);
    }
}
