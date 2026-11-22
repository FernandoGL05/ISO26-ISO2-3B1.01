package es.uclm.esi.iso2.B101.servidor.servicio;

@FunctionalInterface
public interface PoliticaPrescripcionSeguimiento {
    boolean puedePrescribir(String medicoId, String pacienteId);
}
