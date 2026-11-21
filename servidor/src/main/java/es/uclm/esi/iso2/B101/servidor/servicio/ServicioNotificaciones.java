package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.Notificacion;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoNotificacion;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNotificaciones;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public final class ServicioNotificaciones {
    private static final Duration ANTICIPACION_POR_DEFECTO = Duration.ofHours(24);
    private static final String MENSAJE_RECORDATORIO = "Tienes una cita próxima.";
    private static final String MENSAJE_INFORME_DISPONIBLE = "Tu informe está disponible.";

    private final RepositorioNotificaciones repositorio;
    private final Clock reloj;
    private final Duration anticipacionRecordatorio;

    public ServicioNotificaciones(RepositorioNotificaciones repositorio) {
        this(repositorio, Clock.systemDefaultZone(), ANTICIPACION_POR_DEFECTO);
    }

    public ServicioNotificaciones(
            RepositorioNotificaciones repositorio, Clock reloj, Duration anticipacionRecordatorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio no puede ser nulo");
        this.reloj = Objects.requireNonNull(reloj, "reloj no puede ser nulo");
        this.anticipacionRecordatorio = Objects.requireNonNull(
                anticipacionRecordatorio, "anticipacionRecordatorio no puede ser nula");
        if (anticipacionRecordatorio.isZero() || anticipacionRecordatorio.isNegative()) {
            throw new IllegalArgumentException("La anticipación debe ser positiva");
        }
    }

    public List<Notificacion> generarRecordatoriosCitasProximas(Collection<CitaProgramada> citas) {
        Objects.requireNonNull(citas, "citas no puede ser nula");
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<Notificacion> generadas = new ArrayList<>();

        for (CitaProgramada cita : citas) {
            Objects.requireNonNull(cita, "La cita programada no puede ser nula");
            LocalDateTime momentoAviso = cita.fechaHora().minus(anticipacionRecordatorio);
            if (ahora.isBefore(momentoAviso) || !ahora.isBefore(cita.fechaHora())) {
                continue;
            }

            String id = crearIdRecordatorio(cita, momentoAviso);
            Notificacion recordatorio = new Notificacion(
                    id,
                    cita.pacienteId(),
                    TipoNotificacion.RECORDATORIO_CITA,
                    MENSAJE_RECORDATORIO,
                    cita.citaId(),
                    ahora,
                    momentoAviso);
            if (repositorio.guardarSiNoExiste(recordatorio)) {
                generadas.add(recordatorio);
            }
        }
        return generadas;
    }

    public Notificacion registrarPreparacionPrueba(String pacienteId, String pruebaId, String instrucciones) {
        return crearYGuardar(
                pacienteId, TipoNotificacion.PREPARACION_PRUEBA, instrucciones, pruebaId, null);
    }

    public Notificacion registrarInformeDisponible(String pacienteId, String informeId) {
        return crearYGuardar(
                pacienteId,
                TipoNotificacion.INFORME_DISPONIBLE,
                MENSAJE_INFORME_DISPONIBLE,
                informeId,
                null);
    }

    public List<Notificacion> consultarNotificaciones(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return repositorio.buscarPorPacienteId(pacienteId);
    }

    public Notificacion marcarComoLeida(String pacienteId, String notificacionId) {
        validarIdentificador(pacienteId, "pacienteId");
        validarIdentificador(notificacionId, "notificacionId");

        Notificacion notificacion = repositorio.buscarPorId(notificacionId)
                .orElseThrow(() -> new NoSuchElementException("Notificación no encontrada"));
        if (!pacienteId.equals(notificacion.getPacienteId())) {
            throw new NoSuchElementException("Notificación no encontrada");
        }
        repositorio.marcarComoLeida(notificacionId);
        return notificacion;
    }

    private Notificacion crearYGuardar(
            String pacienteId, TipoNotificacion tipo, String mensaje, String referenciaId, LocalDateTime momentoAviso) {
        validarIdentificador(pacienteId, "pacienteId");
        Notificacion notificacion = new Notificacion(
                UUID.randomUUID().toString(),
                pacienteId,
                tipo,
                mensaje,
                referenciaId,
                LocalDateTime.now(reloj),
                momentoAviso);
        repositorio.guardar(notificacion);
        return notificacion;
    }

    private String crearIdRecordatorio(CitaProgramada cita, LocalDateTime momentoAviso) {
        String momentoAvisoTexto = momentoAviso.toString();
        String clave = cita.pacienteId().length() + ":" + cita.pacienteId()
                + cita.citaId().length() + ":" + cita.citaId()
                + momentoAvisoTexto.length() + ":" + momentoAvisoTexto;
        return UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void validarIdentificador(String identificador, String nombre) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }

    public record CitaProgramada(String citaId, String pacienteId, LocalDateTime fechaHora) {
        public CitaProgramada {
            validarCampo(citaId, "citaId");
            validarCampo(pacienteId, "pacienteId");
            if (fechaHora == null) {
                throw new IllegalArgumentException("fechaHora no puede ser nula");
            }
        }

        private static void validarCampo(String valor, String nombre) {
            if (valor == null || valor.isBlank()) {
                throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
            }
        }
    }
}
