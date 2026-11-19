package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Sesion;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio en memoria de sesiones activas e invalidadas.
 */
public class RepositorioSesionesMemoria implements RepositorioSesiones {

    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();

    @Override
    public void guardar(Sesion sesion) {
        sesiones.put(sesion.getToken(), sesion);
    }

    @Override
    public Optional<Sesion> buscarPorToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sesiones.get(token));
    }

    @Override
    public boolean invalidar(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Sesion sesion = sesiones.get(token);
        if (sesion == null || !sesion.isActiva()) {
            return false;
        }
        sesion.invalidar();
        return true;
    }
}
