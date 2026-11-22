package es.uclm.esi.iso2.B101.servidor.servicio;

public final class PrescripcionNoPermitidaException extends RuntimeException {
    public PrescripcionNoPermitidaException() {
        super("El médico no puede prescribir pruebas para este paciente");
    }
}
