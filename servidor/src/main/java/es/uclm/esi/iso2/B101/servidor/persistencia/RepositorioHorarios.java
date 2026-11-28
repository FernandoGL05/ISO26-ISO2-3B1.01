package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.HorarioOperativo;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface RepositorioHorarios {

    void guardar(HorarioOperativo horario);

    Optional<HorarioOperativo> buscarPorId(String id);

    List<HorarioOperativo> buscarPorRecurso(String recursoId);

    List<HorarioOperativo> buscarPorRecursoYDia(String recursoId, DayOfWeek diaSemana);

    List<HorarioOperativo> listarTodos();
}
