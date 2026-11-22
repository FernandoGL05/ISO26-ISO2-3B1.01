package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.EncuestaSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.dominio.RespuestaEncuestaSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioEncuestasSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioRespuestasEncuestas;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public final class ServicioEncuestasSatisfaccion {
    private final RepositorioEncuestasSatisfaccion encuestas;
    private final RepositorioRespuestasEncuestas respuestas;
    private final Clock reloj;

    public ServicioEncuestasSatisfaccion(
            RepositorioEncuestasSatisfaccion encuestas, RepositorioRespuestasEncuestas respuestas) {
        this(encuestas, respuestas, Clock.systemDefaultZone());
    }

    public ServicioEncuestasSatisfaccion(
            RepositorioEncuestasSatisfaccion encuestas,
            RepositorioRespuestasEncuestas respuestas,
            Clock reloj) {
        this.encuestas = Objects.requireNonNull(encuestas, "encuestas no puede ser nulo");
        this.respuestas = Objects.requireNonNull(respuestas, "respuestas no puede ser nulo");
        this.reloj = Objects.requireNonNull(reloj, "reloj no puede ser nulo");
    }

    public List<EncuestaSatisfaccion> consultarEncuestasDisponibles() {
        return encuestas.buscarDisponibles();
    }

    public RespuestaEncuestaSatisfaccion responderEncuesta(
            String pacienteId, String encuestaId, int puntuacion, String comentario) {
        validarIdentificador(pacienteId, "pacienteId");
        validarIdentificador(encuestaId, "encuestaId");
        EncuestaSatisfaccion encuesta = encuestas.buscarPorId(encuestaId)
                .orElseThrow(() -> new NoSuchElementException("Encuesta no encontrada"));
        if (!encuesta.estaDisponible()) {
            throw new IllegalStateException("La encuesta no está disponible");
        }
        if (respuestas.buscarPorEncuestaYPaciente(encuestaId, pacienteId).isPresent()) {
            throw new IllegalStateException("El paciente ya ha respondido a esta encuesta");
        }

        RespuestaEncuestaSatisfaccion respuesta = new RespuestaEncuestaSatisfaccion(
                UUID.randomUUID().toString(),
                encuestaId,
                pacienteId,
                puntuacion,
                comentario,
                LocalDateTime.now(reloj));
        if (!respuestas.guardarSiNoExiste(respuesta)) {
            throw new IllegalStateException("El paciente ya ha respondido a esta encuesta");
        }
        return respuesta;
    }

    public List<RespuestaEncuestaSatisfaccion> consultarMisRespuestas(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return respuestas.buscarPorPaciente(pacienteId);
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
