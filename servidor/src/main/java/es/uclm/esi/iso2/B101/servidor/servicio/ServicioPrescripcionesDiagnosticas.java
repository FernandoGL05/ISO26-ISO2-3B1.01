package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudPruebaDiagnostica;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesDiagnosticas;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ServicioPrescripcionesDiagnosticas {
    private static final Comparator<SolicitudPruebaDiagnostica> ORDEN_RECIENTE_PRIMERO =
            Comparator.comparing(SolicitudPruebaDiagnostica::getFecha)
                    .reversed()
                    .thenComparing(SolicitudPruebaDiagnostica::getId);

    private final RepositorioPrescripcionesDiagnosticas repositorio;
    private final Clock reloj;
    private final PoliticaPrescripcionDiagnostica politica;
    private final Supplier<String> generadorIds;

    public ServicioPrescripcionesDiagnosticas(
            RepositorioPrescripcionesDiagnosticas repositorio,
            Clock reloj,
            PoliticaPrescripcionDiagnostica politica) {
        this(repositorio, reloj, politica, () -> UUID.randomUUID().toString());
    }

    public ServicioPrescripcionesDiagnosticas(
            RepositorioPrescripcionesDiagnosticas repositorio,
            Clock reloj,
            PoliticaPrescripcionDiagnostica politica,
            Supplier<String> generadorIds) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio no puede ser nulo");
        this.reloj = Objects.requireNonNull(reloj, "reloj no puede ser nulo");
        this.politica = Objects.requireNonNull(politica, "politica no puede ser nula");
        this.generadorIds = Objects.requireNonNull(generadorIds, "generadorIds no puede ser nulo");
    }

    public SolicitudPruebaDiagnostica prescribir(
            String medicoId,
            String pacienteId,
            String tipoPrueba,
            String indicacionClinica,
            String justificacion,
            PrioridadPrueba prioridad) {
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(pacienteId, "pacienteId");
        validarObligatorio(tipoPrueba, "tipoPrueba");
        validarObligatorio(indicacionClinica, "indicacionClinica");
        validarObligatorio(justificacion, "justificacion");
        if (prioridad == null) {
            throw new IllegalArgumentException("prioridad no puede ser nula");
        }
        if (!politica.puedePrescribir(medicoId, pacienteId)) {
            throw new PrescripcionNoPermitidaException();
        }
        SolicitudPruebaDiagnostica solicitud = new SolicitudPruebaDiagnostica(
                generadorIds.get(),
                medicoId,
                pacienteId,
                tipoPrueba,
                indicacionClinica,
                justificacion,
                prioridad,
                LocalDateTime.now(reloj));
        repositorio.guardar(solicitud);
        return solicitud;
    }

    public List<SolicitudPruebaDiagnostica> consultarSolicitudes(String medicoId) {
        validarObligatorio(medicoId, "medicoId");
        return repositorio.buscarPorMedicoId(medicoId).stream()
                .sorted(ORDEN_RECIENTE_PRIMERO)
                .toList();
    }

    public SolicitudPruebaDiagnostica consultarDetalle(String medicoId, String id) {
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(id, "id");
        SolicitudPruebaDiagnostica solicitud = repositorio.buscarPorId(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada"));
        if (!solicitud.getMedicoId().equals(medicoId)) {
            throw new NoSuchElementException("Solicitud no encontrada");
        }
        return solicitud;
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
