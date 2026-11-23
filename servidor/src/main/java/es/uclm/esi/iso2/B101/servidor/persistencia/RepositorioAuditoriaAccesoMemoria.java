package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.RegistroAuditoriaAcceso;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementación en memoria del registro de auditoría de accesos.
 */
public class RepositorioAuditoriaAccesoMemoria implements RepositorioAuditoriaAcceso {

    private final List<RegistroAuditoriaAcceso> registros = new CopyOnWriteArrayList<>();

    @Override
    public void registrar(RegistroAuditoriaAcceso registro) {
        registros.add(registro);
    }

    @Override
    public List<RegistroAuditoriaAcceso> obtenerTodos() {
        return Collections.unmodifiableList(registros);
    }
}
