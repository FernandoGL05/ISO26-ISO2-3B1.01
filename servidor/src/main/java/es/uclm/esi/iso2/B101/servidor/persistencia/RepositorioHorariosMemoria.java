package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.HorarioOperativo;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioHorariosMemoria implements RepositorioHorarios {

    private final Map<String, HorarioOperativo> almacenamiento = new ConcurrentHashMap<>();

    @Override
    public void guardar(HorarioOperativo horario) {
        Objects.requireNonNull(horario, "horario no puede ser nulo");
        almacenamiento.put(horario.getId(), horario);
    }

    @Override
    public Optional<HorarioOperativo> buscarPorId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(almacenamiento.get(id.trim()));
    }

    @Override
    public List<HorarioOperativo> buscarPorRecurso(String recursoId) {
        if (recursoId == null || recursoId.isBlank()) {
            return List.of();
        }
        String buscado = recursoId.trim().toLowerCase(Locale.ROOT);
        return almacenamiento.values().stream()
                .filter(h -> h.getRecursoId().toLowerCase(Locale.ROOT).equals(buscado))
                .sorted(Comparator.comparing(HorarioOperativo::getDiaSemana)
                        .thenComparing(HorarioOperativo::getHoraInicio)
                        .thenComparing(HorarioOperativo::getId))
                .toList();
    }

    @Override
    public List<HorarioOperativo> buscarPorRecursoYDia(String recursoId, DayOfWeek diaSemana) {
        if (recursoId == null || recursoId.isBlank() || diaSemana == null) {
            return List.of();
        }
        String buscado = recursoId.trim().toLowerCase(Locale.ROOT);
        return almacenamiento.values().stream()
                .filter(h -> h.getRecursoId().toLowerCase(Locale.ROOT).equals(buscado)
                        && h.getDiaSemana() == diaSemana)
                .sorted(Comparator.comparing(HorarioOperativo::getHoraInicio)
                        .thenComparing(HorarioOperativo::getId))
                .toList();
    }

    @Override
    public List<HorarioOperativo> listarTodos() {
        return almacenamiento.values().stream()
                .sorted(Comparator.comparing(HorarioOperativo::getId))
                .toList();
    }
}
