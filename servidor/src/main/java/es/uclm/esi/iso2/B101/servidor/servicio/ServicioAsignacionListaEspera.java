package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioListaEspera;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public final class ServicioAsignacionListaEspera {
    private final RepositorioListaEspera repositorioLista;
    private final RepositorioCitas repositorioCitas;
    private final Clock reloj;

    public ServicioAsignacionListaEspera(
            RepositorioListaEspera repositorioLista,
            RepositorioCitas repositorioCitas) {
        this(repositorioLista, repositorioCitas, Clock.systemDefaultZone());
    }

    public ServicioAsignacionListaEspera(
            RepositorioListaEspera repositorioLista,
            RepositorioCitas repositorioCitas,
            Clock reloj) {
        this.repositorioLista = Objects.requireNonNull(
                repositorioLista, "repositorioLista es obligatorio");
        this.repositorioCitas = Objects.requireNonNull(
                repositorioCitas, "repositorioCitas es obligatorio");
        this.reloj = Objects.requireNonNull(
                reloj, "reloj es obligatorio");
    }

    public ResultadoAsignacion asignarSiguiente(
            String medicoId,
            String tipoPrueba,
            LocalDateTime fechaHora,
            int duracionMinutos) {
        validarPeticion(medicoId, tipoPrueba, fechaHora, duracionMinutos);

        // Compartimos el bloqueo con las operaciones de la lista:
        // no puede cancelarse o cambiar de prioridad mientras se asigna.
        synchronized (repositorioLista) {
            EntradaListaEspera entrada = seleccionarSiguiente(
                    medicoId, tipoPrueba);

            Cita cita = new Cita(
                    UUID.randomUUID().toString(),
                    entrada.getPacienteId(),
                    entrada.getMedicoId(),
                    entrada.getTipoPrueba(),
                    fechaHora,
                    duracionMinutos);

            EntradaListaEspera citada = entrada.asignarCita(cita.getId());

            // Mantenemos el bloqueo hasta actualizar los dos repositorios.
            synchronized (repositorioCitas) {
                repositorioCitas.reservar(cita);

                try {
                    repositorioLista.guardar(citada);
                } catch (RuntimeException error) {
                    try {
                        repositorioCitas.deshacerReserva(cita);
                    } catch (RuntimeException errorDeshacer) {
                        error.addSuppressed(errorDeshacer);
                    }
                    throw error;
                }

                return new ResultadoAsignacion(citada, cita);
            }
        }
    }

    private EntradaListaEspera seleccionarSiguiente(
            String medicoId,
            String tipoPrueba) {
        return repositorioLista.buscarPendientes().stream()
                .filter(entrada ->
                        entrada.getMedicoId().equals(medicoId)
                        && entrada.getTipoPrueba().equals(tipoPrueba))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No hay entradas pendientes para ese medico y prueba"));
    }

    private void validarPeticion(
            String medicoId,
            String tipoPrueba,
            LocalDateTime fechaHora,
            int duracionMinutos) {
        validarTexto(medicoId, "medicoId");
        validarTexto(tipoPrueba, "tipoPrueba");

        if (fechaHora == null) {
            throw new IllegalArgumentException("fechaHora es obligatoria");
        }

        if (!fechaHora.isAfter(LocalDateTime.now(reloj))) {
            throw new IllegalArgumentException(
                    "La cita debe estar en el futuro");
        }

        if (duracionMinutos <= 0 || duracionMinutos > 1440) {
            throw new IllegalArgumentException(
                    "duracionMinutos debe estar entre 1 y 1440");
        }

        try {
            fechaHora.plusMinutes(duracionMinutos);
        } catch (DateTimeException error) {
            throw new IllegalArgumentException(
                    "La fecha de fin de la cita no es valida", error);
        }
    }

    private static void validarTexto(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " no puede ser nulo ni estar vacio");
        }
    }

    public record ResultadoAsignacion(
            EntradaListaEspera entrada,
            Cita cita) {
        public ResultadoAsignacion {
            Objects.requireNonNull(entrada, "entrada es obligatoria");
            Objects.requireNonNull(cita, "cita es obligatoria");
        }
    }
}