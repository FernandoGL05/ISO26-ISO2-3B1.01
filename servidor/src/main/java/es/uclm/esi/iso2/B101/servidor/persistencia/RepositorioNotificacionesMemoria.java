package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Notificacion;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioNotificacionesMemoria implements RepositorioNotificaciones {
    private final ConcurrentHashMap<String, Notificacion> notificaciones = new ConcurrentHashMap<>();

    @Override
    public void guardar(Notificacion notificacion) {
        if (notificacion == null) {
            throw new IllegalArgumentException("La notificación no puede ser nula");
        }
        notificaciones.put(notificacion.getId(), notificacion);
    }

    @Override
    public boolean guardarSiNoExiste(Notificacion notificacion) {
        if (notificacion == null) {
            throw new IllegalArgumentException("La notificación no puede ser nula");
        }
        return notificaciones.putIfAbsent(notificacion.getId(), notificacion) == null;
    }

    @Override
    public Optional<Notificacion> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(notificaciones.get(id));
    }

    @Override
    public List<Notificacion> buscarPorPacienteId(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");

        List<Notificacion> resultado = new ArrayList<>();
        for (Notificacion notificacion : notificaciones.values()) {
            if (notificacion.getPacienteId().equals(pacienteId)) {
                resultado.add(notificacion);
            }
        }
        return resultado;
    }

    @Override
    public void marcarComoLeida(String id) {
        validarIdentificador(id, "id");
        Notificacion notificacion = notificaciones.get(id);
        if (notificacion == null) {
            throw new NoSuchElementException("Notificación no encontrada");
        }
        notificacion.marcarComoLeida();
    }

    private void validarIdentificador(String identificador, String nombre) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
