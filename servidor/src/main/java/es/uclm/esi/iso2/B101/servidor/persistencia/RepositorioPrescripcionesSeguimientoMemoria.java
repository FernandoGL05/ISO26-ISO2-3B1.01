package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.PrescripcionSeguimiento;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioPrescripcionesSeguimientoMemoria implements RepositorioPrescripcionesSeguimiento {
    private final ConcurrentHashMap<String, PrescripcionSeguimiento> prescripciones = new ConcurrentHashMap<>();

    @Override
    public void guardar(PrescripcionSeguimiento prescripcion) {
        if (prescripcion == null) {
            throw new IllegalArgumentException("La prescripción no puede ser nula");
        }
        if (prescripciones.putIfAbsent(prescripcion.getId(), prescripcion) != null) {
            throw new IllegalStateException("Ya existe una prescripción con ese identificador");
        }
    }

    @Override
    public Optional<PrescripcionSeguimiento> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(prescripciones.get(id));
    }

    @Override
    public List<PrescripcionSeguimiento> buscarPorMedicoId(String medicoId) {
        validarIdentificador(medicoId, "medicoId");
        return prescripciones.values().stream()
                .filter(prescripcion -> prescripcion.getMedicoId().equals(medicoId))
                .toList();
    }

    @Override
    public List<PrescripcionSeguimiento> buscarTodas() {
        return List.copyOf(prescripciones.values());
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
