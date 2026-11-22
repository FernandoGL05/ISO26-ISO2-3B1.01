package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioSeguimientosMemoria implements RepositorioSeguimientos {
    private final ConcurrentHashMap<String, SeguimientoPaciente> seguimientos = new ConcurrentHashMap<>();

    @Override
    public void registrar(SeguimientoPaciente seguimiento) {
        if (seguimiento == null) {
            throw new IllegalArgumentException("El seguimiento no puede ser nulo");
        }
        if (seguimientos.putIfAbsent(seguimiento.getId(), seguimiento) != null) {
            throw new IllegalStateException("Ya existe un seguimiento con ese identificador");
        }
    }

    @Override
    public Optional<SeguimientoPaciente> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(seguimientos.get(id));
    }

    @Override
    public List<SeguimientoPaciente> buscarPorPacienteId(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return seguimientos.values().stream()
                .filter(seguimiento -> seguimiento.getPacienteId().equals(pacienteId))
                .toList();
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
