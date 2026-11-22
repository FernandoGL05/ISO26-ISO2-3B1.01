package es.uclm.esi.iso2.B101.servidor.servicio;

public final class PrescripcionSeguimientoNoPermitidaException extends RuntimeException {
    public PrescripcionSeguimientoNoPermitidaException() {
        super("El médico no puede prescribir un seguimiento para este paciente");
    }
}
