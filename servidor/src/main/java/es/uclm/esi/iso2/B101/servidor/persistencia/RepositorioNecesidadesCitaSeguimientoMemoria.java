package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.NecesidadCitaSeguimiento;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RepositorioNecesidadesCitaSeguimientoMemoria
        implements RepositorioNecesidadesCitaSeguimiento {
    private final Map<ClaveNecesidad, NecesidadCitaSeguimiento> necesidades = new HashMap<>();
    private final Map<String, ClaveNecesidad> clavesPorId = new HashMap<>();

    @Override
    public synchronized boolean guardarSiNoExiste(NecesidadCitaSeguimiento necesidad) {
        if (necesidad == null) {
            throw new IllegalArgumentException("La necesidad no puede ser nula");
        }
        ClaveNecesidad clave = new ClaveNecesidad(necesidad.getPrescripcionId(), necesidad.getFechaPrevista());
        if (necesidades.containsKey(clave)) {
            return false;
        }
        ClaveNecesidad claveConId = clavesPorId.get(necesidad.getId());
        if (claveConId != null) {
            throw new IllegalStateException("Ya existe una necesidad con ese identificador");
        }
        necesidades.put(clave, necesidad);
        clavesPorId.put(necesidad.getId(), clave);
        return true;
    }

    @Override
    public synchronized Optional<NecesidadCitaSeguimiento> buscarPorPrescripcionYFecha(
            String prescripcionId, LocalDate fechaPrevista) {
        validarIdentificador(prescripcionId, "prescripcionId");
        if (fechaPrevista == null) {
            throw new IllegalArgumentException("fechaPrevista no puede ser nula");
        }
        return Optional.ofNullable(necesidades.get(new ClaveNecesidad(prescripcionId, fechaPrevista)));
    }

    @Override
    public synchronized List<NecesidadCitaSeguimiento> buscarPorPrescripcionId(String prescripcionId) {
        validarIdentificador(prescripcionId, "prescripcionId");
        List<NecesidadCitaSeguimiento> resultado = new ArrayList<>();
        for (Map.Entry<ClaveNecesidad, NecesidadCitaSeguimiento> entrada : necesidades.entrySet()) {
            if (entrada.getKey().prescripcionId.equals(prescripcionId)) {
                resultado.add(entrada.getValue());
            }
        }
        return List.copyOf(resultado);
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }

    private record ClaveNecesidad(String prescripcionId, LocalDate fechaPrevista) {
    }
}
