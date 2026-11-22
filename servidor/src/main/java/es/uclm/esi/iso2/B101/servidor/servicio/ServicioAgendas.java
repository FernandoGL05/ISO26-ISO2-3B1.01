package es.uclm.esi.iso2.B101.servidor.servicio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import es.uclm.esi.iso2.B101.servidor.dominio.AgendaDiaria;
import es.uclm.esi.iso2.B101.servidor.dominio.HuecoAgenda;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAgendas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHuecosAgenda;

public final class ServicioAgendas {
    private final RepositorioAgendas repositorioAgendas;
    private final RepositorioHuecosAgenda repositorioHuecos;

    public ServicioAgendas(
            RepositorioAgendas repositorioAgendas,
            RepositorioHuecosAgenda repositorioHuecos) {

        this.repositorioAgendas = Objects.requireNonNull(
                repositorioAgendas, "repositorioAgendas no puede ser nulo");

        this.repositorioHuecos = Objects.requireNonNull(
                repositorioHuecos, "repositorioHuecos no puede ser nulo");
    }

    public AgendaDiaria crearAgenda(String medicoId, LocalDate fecha) {
        AgendaDiaria agenda = new AgendaDiaria(
                UUID.randomUUID().toString(), medicoId, fecha);

        repositorioAgendas.guardar(agenda);
        return agenda;
    }

    public AgendaDiaria consultarAgenda(String agendaId) {
        validarIdentificador(agendaId, "agendaId");

        return repositorioAgendas.buscarPorId(agendaId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Agenda no encontrada"));
    }

    public AgendaDiaria consultarAgendaPorMedicoYFecha(
            String medicoId, LocalDate fecha) {

        validarIdentificador(medicoId, "medicoId");
        validarFecha(fecha);

        return repositorioAgendas.buscarPorMedicoYFecha(medicoId, fecha)
                .orElseThrow(() -> new NoSuchElementException(
                        "Agenda no encontrada"));
    }

    public List<AgendaDiaria> consultarAgendasPorFecha(LocalDate fecha) {
        validarFecha(fecha);
        return repositorioAgendas.buscarPorFecha(fecha);
    }

    public HuecoAgenda agregarHueco(
            String agendaId, LocalTime horaInicio, LocalTime horaFin) {

        consultarAgenda(agendaId);

        HuecoAgenda hueco = new HuecoAgenda(
                UUID.randomUUID().toString(),
                agendaId,
                horaInicio,
                horaFin);

        repositorioHuecos.guardar(hueco);
        return hueco;
    }

    public List<HuecoAgenda> consultarHuecos(String agendaId) {
        consultarAgenda(agendaId);
        return repositorioHuecos.buscarPorAgendaId(agendaId);
    }

    public HuecoAgenda modificarHueco(
            String agendaId,
            String huecoId,
            LocalTime horaInicio,
            LocalTime horaFin) {

        buscarHuecoDeAgenda(agendaId, huecoId);

        HuecoAgenda actualizado = new HuecoAgenda(
                huecoId, agendaId, horaInicio, horaFin);

        repositorioHuecos.guardar(actualizado);
        return actualizado;
    }

    public void eliminarHueco(String agendaId, String huecoId) {
        buscarHuecoDeAgenda(agendaId, huecoId);
        repositorioHuecos.eliminar(huecoId);
    }

    private HuecoAgenda buscarHuecoDeAgenda(
            String agendaId, String huecoId) {

        consultarAgenda(agendaId);
        validarIdentificador(huecoId, "huecoId");

        HuecoAgenda hueco = repositorioHuecos.buscarPorId(huecoId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Hueco de agenda no encontrado"));

        if (!agendaId.equals(hueco.getAgendaId())) {
            throw new NoSuchElementException(
                    "Hueco de agenda no encontrado");
        }

        return hueco;
    }

    private static void validarIdentificador(
            String valor, String nombre) {

        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " no puede ser nulo ni estar vacío");
        }
    }

    private static void validarFecha(LocalDate fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException(
                    "La fecha no puede ser nula");
        }
    }
}