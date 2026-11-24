package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoCita;
import es.uclm.esi.iso2.B101.servidor.dominio.HorarioOperativo;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoPrueba;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHorarios;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioTiposPrueba;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class ServicioAgendas {
    private final RepositorioCitas repositorioCitas;
    private final RepositorioHorarios repositorioHorarios;
    private final RepositorioTiposPrueba repositorioTiposPrueba;

    public ServicioAgendas(RepositorioCitas repositorioCitas) {
        this.repositorioCitas = Objects.requireNonNull(repositorioCitas, "repositorioCitas no puede ser nulo");
        this.repositorioHorarios = null;
        this.repositorioTiposPrueba = null;
    }

    public ServicioAgendas(
            RepositorioCitas repositorioCitas,
            RepositorioHorarios repositorioHorarios,
            RepositorioTiposPrueba repositorioTiposPrueba) {
        this.repositorioCitas = Objects.requireNonNull(repositorioCitas, "repositorioCitas no puede ser nulo");
        this.repositorioHorarios = Objects.requireNonNull(repositorioHorarios, "repositorioHorarios no puede ser nulo");
        this.repositorioTiposPrueba = Objects.requireNonNull(
                repositorioTiposPrueba, "repositorioTiposPrueba no puede ser nulo");
    }

    public List<HuecoDisponible> consultarDisponibilidadConfigurada(
            String medicoId,
            String tipoPruebaId,
            LocalDate fecha) {
        if (repositorioHorarios == null || repositorioTiposPrueba == null) {
            throw new IllegalStateException(
                    "Se requiere RepositorioHorarios y RepositorioTiposPrueba para consultar disponibilidad configurada");
        }
        validarIdentificador(medicoId, "medicoId");
        validarIdentificador(tipoPruebaId, "tipoPruebaId");
        Objects.requireNonNull(fecha, "fecha no puede ser nula");

        TipoPrueba tipoPrueba = repositorioTiposPrueba.buscarPorId(tipoPruebaId)
                .orElseThrow(() -> new NoSuchElementException("Tipo de prueba no encontrado: " + tipoPruebaId));
        if (!tipoPrueba.isActivo()) {
            throw new IllegalStateException("El tipo de prueba no está activo: " + tipoPruebaId);
        }

        List<HorarioOperativo> horariosActivos = repositorioHorarios
                .buscarPorRecursoYDia(medicoId, fecha.getDayOfWeek())
                .stream()
                .filter(HorarioOperativo::isActivo)
                .toList();

        List<HuecoDisponible> huecos = new java.util.ArrayList<>();
        for (HorarioOperativo horario : horariosActivos) {
            LocalDateTime desde = LocalDateTime.of(fecha, horario.getHoraInicio());
            LocalDateTime hasta = LocalDateTime.of(fecha, horario.getHoraFin());
            huecos.addAll(consultarDisponibilidad(
                    medicoId,
                    tipoPrueba.getNombre(),
                    desde,
                    hasta,
                    tipoPrueba.getDuracionEstimadaMinutos()));
        }
        return List.copyOf(huecos);
    }

    public List<HuecoDisponible> consultarDisponibilidad(
            String medicoId,
            String tipoPrueba,
            LocalDateTime desde,
            LocalDateTime hasta,
            int duracionMinutos) {
        validarIdentificador(medicoId, "medicoId");
        validarIdentificador(tipoPrueba, "tipoPrueba");
        if (desde == null || hasta == null || !desde.isBefore(hasta) || !desde.toLocalDate().equals(hasta.toLocalDate())) {
            throw new IllegalArgumentException("El intervalo debe ser válido y pertenecer a un único día");
        }
        if (duracionMinutos <= 0 || duracionMinutos > 24 * 60) {
            throw new IllegalArgumentException("duracionMinutos debe estar entre 1 y 1440");
        }

        List<Cita> citasActivas = repositorioCitas.buscarPorMedicoId(medicoId).stream()
                .filter(cita -> cita.getEstado() != EstadoCita.CANCELADA)
                .toList();
        return calcularHuecos(tipoPrueba, desde, hasta, duracionMinutos, citasActivas);
    }

    private List<HuecoDisponible> calcularHuecos(
            String tipoPrueba,
            LocalDateTime desde,
            LocalDateTime hasta,
            int duracionMinutos,
            List<Cita> citas) {
        List<HuecoDisponible> disponibles = new java.util.ArrayList<>();
        LocalDateTime finHueco = desde.plusMinutes(duracionMinutos);
        while (!finHueco.isAfter(hasta)) {
            LocalDateTime inicioHueco = finHueco.minusMinutes(duracionMinutos);
            LocalDateTime finCandidato = finHueco;
            boolean ocupado = citas.stream().anyMatch(cita -> {
                LocalDateTime finCita = cita.getFechaHora().plusMinutes(duracionMinutos);
                return inicioHueco.isBefore(finCita) && cita.getFechaHora().isBefore(finCandidato);
            });
            if (!ocupado) {
                disponibles.add(new HuecoDisponible(tipoPrueba, inicioHueco, finHueco));
            }
            finHueco = finHueco.plusMinutes(duracionMinutos);
        }
        return List.copyOf(disponibles);
    }

    private void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }

    public record HuecoDisponible(String tipoPrueba, LocalDateTime desde, LocalDateTime hasta) {
    }
}
