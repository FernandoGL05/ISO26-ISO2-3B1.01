package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.HorarioOperativo;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoRecursoHorario;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHorarios;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de aplicación para la configuración de horarios operativos de salas, equipos y médicos (CU22 / RF22).
 */
public final class ServicioHorarios {

    private final RepositorioHorarios repositorioHorarios;
    private final AtomicLong secuenciaId = new AtomicLong(1);

    public ServicioHorarios(RepositorioHorarios repositorioHorarios) {
        this.repositorioHorarios = Objects.requireNonNull(
                repositorioHorarios, "repositorioHorarios no puede ser nulo");
    }

    public HorarioOperativo crearHorario(
            String recursoId,
            TipoRecursoHorario tipoRecurso,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            int duracionHuecoMinutos) {
        String id = "HOR-" + secuenciaId.getAndIncrement();
        HorarioOperativo nuevo = new HorarioOperativo(
                id,
                recursoId,
                tipoRecurso,
                diaSemana,
                horaInicio,
                horaFin,
                duracionHuecoMinutos,
                true);
        validarSinSolapamientos(nuevo, null);
        repositorioHorarios.guardar(nuevo);
        return nuevo;
    }

    public HorarioOperativo actualizarHorario(
            String id,
            String recursoId,
            TipoRecursoHorario tipoRecurso,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            int duracionHuecoMinutos) {
        HorarioOperativo existente = obtenerPorId(id);
        HorarioOperativo actualizado = existente.conActualizacion(
                recursoId,
                tipoRecurso,
                diaSemana,
                horaInicio,
                horaFin,
                duracionHuecoMinutos);
        if (actualizado.isActivo()) {
            validarSinSolapamientos(actualizado, existente.getId());
        }
        repositorioHorarios.guardar(actualizado);
        return actualizado;
    }

    public HorarioOperativo cambiarEstado(String id, boolean activo) {
        HorarioOperativo existente = obtenerPorId(id);
        if (activo) {
            validarSinSolapamientos(existente, existente.getId());
            existente.activar();
        } else {
            existente.desactivar();
        }
        repositorioHorarios.guardar(existente);
        return existente;
    }

    public HorarioOperativo obtenerPorId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser nulo ni estar vacío");
        }
        return repositorioHorarios.buscarPorId(id)
                .orElseThrow(() -> new NoSuchElementException("Horario no encontrado: " + id));
    }

    public List<HorarioOperativo> listarTodos() {
        return repositorioHorarios.listarTodos();
    }

    public List<HorarioOperativo> listarPorRecurso(String recursoId) {
        if (recursoId == null || recursoId.isBlank()) {
            throw new IllegalArgumentException("recursoId no puede ser nulo ni estar vacío");
        }
        return repositorioHorarios.buscarPorRecurso(recursoId);
    }

    public List<HorarioOperativo> listarActivosPorRecursoYDia(String recursoId, DayOfWeek diaSemana) {
        if (recursoId == null || recursoId.isBlank()) {
            throw new IllegalArgumentException("recursoId no puede ser nulo ni estar vacío");
        }
        Objects.requireNonNull(diaSemana, "diaSemana no puede ser nulo");
        return repositorioHorarios.buscarPorRecursoYDia(recursoId, diaSemana).stream()
                .filter(HorarioOperativo::isActivo)
                .toList();
    }

    private void validarSinSolapamientos(HorarioOperativo candidato, String idExcluido) {
        boolean solapa = repositorioHorarios
                .buscarPorRecursoYDia(candidato.getRecursoId(), candidato.getDiaSemana())
                .stream()
                .filter(HorarioOperativo::isActivo)
                .filter(existente -> !existente.getId().equals(idExcluido))
                .anyMatch(candidato::seSolapaCon);
        if (solapa) {
            throw new IllegalStateException(
                    "El horario se solapa con otro horario activo para el recurso " + candidato.getRecursoId());
        }
    }
}
