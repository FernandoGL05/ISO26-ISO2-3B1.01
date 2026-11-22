package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.NecesidadCitaSeguimiento;
import es.uclm.esi.iso2.B101.servidor.dominio.PrescripcionSeguimiento;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadSeguimiento;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNecesidadesCitaSeguimiento;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesSeguimiento;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ServicioPrescripcionesSeguimiento {
    public static final int HORIZONTE_INICIAL_PREDETERMINADO_MESES = 12;
    public static final int HORIZONTE_MAXIMO_MESES = 120;
    private static final Comparator<PrescripcionSeguimiento> ORDEN_PRESCRIPCIONES =
            Comparator.comparing(PrescripcionSeguimiento::getFechaCreacion)
                    .reversed()
                    .thenComparing(PrescripcionSeguimiento::getId);
    private static final Comparator<NecesidadCitaSeguimiento> ORDEN_NECESIDADES =
            Comparator.comparing(NecesidadCitaSeguimiento::getFechaPrevista)
                    .thenComparing(NecesidadCitaSeguimiento::getId);

    private final RepositorioPrescripcionesSeguimiento repositorioPrescripciones;
    private final RepositorioNecesidadesCitaSeguimiento repositorioNecesidades;
    private final Clock reloj;
    private final PoliticaPrescripcionSeguimiento politica;
    private final int horizonteInicialMeses;
    private final int horizonteMaximoMeses;
    private final Supplier<String> generadorIds;

    public ServicioPrescripcionesSeguimiento(
            RepositorioPrescripcionesSeguimiento repositorioPrescripciones,
            RepositorioNecesidadesCitaSeguimiento repositorioNecesidades,
            Clock reloj,
            PoliticaPrescripcionSeguimiento politica) {
        this(
                repositorioPrescripciones,
                repositorioNecesidades,
                reloj,
                politica,
                HORIZONTE_INICIAL_PREDETERMINADO_MESES,
                HORIZONTE_MAXIMO_MESES);
    }

    public ServicioPrescripcionesSeguimiento(
            RepositorioPrescripcionesSeguimiento repositorioPrescripciones,
            RepositorioNecesidadesCitaSeguimiento repositorioNecesidades,
            Clock reloj,
            PoliticaPrescripcionSeguimiento politica,
            int horizonteInicialMeses,
            int horizonteMaximoMeses) {
        this(
                repositorioPrescripciones,
                repositorioNecesidades,
                reloj,
                politica,
                horizonteInicialMeses,
                horizonteMaximoMeses,
                () -> UUID.randomUUID().toString());
    }

    public ServicioPrescripcionesSeguimiento(
            RepositorioPrescripcionesSeguimiento repositorioPrescripciones,
            RepositorioNecesidadesCitaSeguimiento repositorioNecesidades,
            Clock reloj,
            PoliticaPrescripcionSeguimiento politica,
            int horizonteInicialMeses,
            int horizonteMaximoMeses,
            Supplier<String> generadorIds) {
        this.repositorioPrescripciones =
                Objects.requireNonNull(repositorioPrescripciones, "repositorioPrescripciones no puede ser nulo");
        this.repositorioNecesidades =
                Objects.requireNonNull(repositorioNecesidades, "repositorioNecesidades no puede ser nulo");
        this.reloj = Objects.requireNonNull(reloj, "reloj no puede ser nulo");
        this.politica = Objects.requireNonNull(politica, "politica no puede ser nula");
        if (horizonteMaximoMeses <= 0 || horizonteMaximoMeses > HORIZONTE_MAXIMO_MESES) {
            throw new IllegalArgumentException(
                    "horizonteMaximoMeses debe estar entre 1 y " + HORIZONTE_MAXIMO_MESES);
        }
        if (horizonteInicialMeses <= 0 || horizonteInicialMeses > horizonteMaximoMeses) {
            throw new IllegalArgumentException(
                    "horizonteInicialMeses debe estar entre 1 y horizonteMaximoMeses");
        }
        this.horizonteInicialMeses = horizonteInicialMeses;
        this.horizonteMaximoMeses = horizonteMaximoMeses;
        this.generadorIds = Objects.requireNonNull(generadorIds, "generadorIds no puede ser nulo");
    }

    public PrescripcionSeguimiento registrarPrescripcion(
            String medicoId,
            String pacienteId,
            String tipoPrueba,
            String motivoClinico,
            int periodicidadMeses,
            LocalDate fechaPrimeraRevision,
            PrioridadSeguimiento prioridad) {
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(pacienteId, "pacienteId");
        validarObligatorio(tipoPrueba, "tipoPrueba");
        validarObligatorio(motivoClinico, "motivoClinico");
        if (periodicidadMeses <= 0) {
            throw new IllegalArgumentException("periodicidadMeses debe ser positiva");
        }
        if (fechaPrimeraRevision == null) {
            throw new IllegalArgumentException("fechaPrimeraRevision no puede ser nula");
        }
        if (prioridad == null) {
            throw new IllegalArgumentException("prioridad no puede ser nula");
        }

        LocalDate hoy = LocalDate.now(reloj);
        LocalDate limiteMaximo = sumarMeses(hoy, horizonteMaximoMeses);
        if (fechaPrimeraRevision.isBefore(hoy) || fechaPrimeraRevision.isAfter(limiteMaximo)) {
            throw new IllegalArgumentException(
                    "fechaPrimeraRevision debe estar entre hoy y el horizonte máximo permitido");
        }
        if (!politica.puedePrescribir(medicoId, pacienteId)) {
            throw new PrescripcionSeguimientoNoPermitidaException();
        }

        PrescripcionSeguimiento prescripcion = new PrescripcionSeguimiento(
                generadorIds.get(),
                medicoId,
                pacienteId,
                tipoPrueba,
                motivoClinico,
                periodicidadMeses,
                fechaPrimeraRevision,
                prioridad,
                hoy);
        repositorioPrescripciones.guardar(prescripcion);
        generarNecesidades(prescripcion, sumarMeses(hoy, horizonteInicialMeses), hoy);
        return prescripcion;
    }

    public List<PrescripcionSeguimiento> consultarPrescripciones(String medicoId) {
        validarObligatorio(medicoId, "medicoId");
        return repositorioPrescripciones.buscarPorMedicoId(medicoId).stream()
                .sorted(ORDEN_PRESCRIPCIONES)
                .toList();
    }

    public PrescripcionSeguimiento consultarDetalle(String medicoId, String prescripcionId) {
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(prescripcionId, "prescripcionId");
        return buscarPropia(medicoId, prescripcionId);
    }

    public List<NecesidadCitaSeguimiento> consultarNecesidadesCita(String medicoId, String prescripcionId) {
        PrescripcionSeguimiento prescripcion = buscarPropia(medicoId, prescripcionId);
        return necesidadesOrdenadas(prescripcion.getId());
    }

    public int generarNecesidadesHasta(
            String medicoId, String prescripcionId, LocalDate fechaLimite) {
        PrescripcionSeguimiento prescripcion = buscarPropia(medicoId, prescripcionId);
        LocalDate hoy = LocalDate.now(reloj);
        validarLimite(fechaLimite, hoy);
        return generarNecesidades(prescripcion, fechaLimite, hoy);
    }

    public int generarNecesidadesHasta(LocalDate fechaLimite) {
        LocalDate hoy = LocalDate.now(reloj);
        validarLimite(fechaLimite, hoy);
        int generadas = 0;
        for (PrescripcionSeguimiento prescripcion : repositorioPrescripciones.buscarTodas()) {
            generadas += generarNecesidades(prescripcion, fechaLimite, hoy);
        }
        return generadas;
    }

    public int getHorizonteInicialMeses() {
        return horizonteInicialMeses;
    }

    public int getHorizonteMaximoMeses() {
        return horizonteMaximoMeses;
    }

    private PrescripcionSeguimiento buscarPropia(String medicoId, String prescripcionId) {
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(prescripcionId, "prescripcionId");
        PrescripcionSeguimiento prescripcion = repositorioPrescripciones.buscarPorId(prescripcionId)
                .orElseThrow(() -> new NoSuchElementException("Prescripción no encontrada"));
        if (!prescripcion.getMedicoId().equals(medicoId)) {
            throw new NoSuchElementException("Prescripción no encontrada");
        }
        return prescripcion;
    }

    private int generarNecesidades(PrescripcionSeguimiento prescripcion, LocalDate fechaLimite, LocalDate hoy) {
        LocalDate primera = prescripcion.getFechaPrimeraRevision();
        long mesesHastaHoy = Math.max(0, ChronoUnit.MONTHS.between(primera, hoy));
        long indice = mesesHastaHoy / prescripcion.getPeriodicidadMeses();
        LocalDate fecha = fechaDeRecurrencia(prescripcion, indice);
        while (fecha != null && fecha.isBefore(hoy)) {
            fecha = fechaDeRecurrencia(prescripcion, ++indice);
        }

        int nuevas = 0;
        int examinadas = 0;
        while (fecha != null && !fecha.isAfter(fechaLimite)) {
            if (examinadas++ >= horizonteMaximoMeses + 1) {
                throw new IllegalStateException("El horizonte generó más necesidades de las permitidas");
            }
            NecesidadCitaSeguimiento necesidad = new NecesidadCitaSeguimiento(
                    generadorIds.get(),
                    prescripcion.getId(),
                    prescripcion.getMedicoId(),
                    prescripcion.getPacienteId(),
                    prescripcion.getTipoPrueba(),
                    fecha);
            if (repositorioNecesidades.guardarSiNoExiste(necesidad)) {
                nuevas++;
            }
            fecha = fechaDeRecurrencia(prescripcion, ++indice);
        }
        return nuevas;
    }

    private LocalDate fechaDeRecurrencia(PrescripcionSeguimiento prescripcion, long indice) {
        try {
            long meses = Math.multiplyExact(indice, (long) prescripcion.getPeriodicidadMeses());
            return prescripcion.getFechaPrimeraRevision().plusMonths(meses);
        } catch (ArithmeticException | DateTimeException error) {
            return null;
        }
    }

    private List<NecesidadCitaSeguimiento> necesidadesOrdenadas(String prescripcionId) {
        return repositorioNecesidades.buscarPorPrescripcionId(prescripcionId).stream()
                .sorted(ORDEN_NECESIDADES)
                .toList();
    }

    private void validarLimite(LocalDate fechaLimite, LocalDate hoy) {
        if (fechaLimite == null) {
            throw new IllegalArgumentException("fechaLimite no puede ser nula");
        }
        if (fechaLimite.isAfter(sumarMeses(hoy, horizonteMaximoMeses))) {
            throw new IllegalArgumentException("fechaLimite supera el horizonte máximo permitido");
        }
    }

    private static LocalDate sumarMeses(LocalDate fecha, int meses) {
        try {
            return fecha.plusMonths(meses);
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("La fecha excede el intervalo admitido", error);
        }
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
