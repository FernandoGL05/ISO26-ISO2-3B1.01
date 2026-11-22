# ISO26-ISO2-3B1.01

## Consulta de disponibilidad de agendas

El personal médico puede consultar los huecos libres de su agenda diaria mediante:

```text
GET /api/agendas/disponibilidad?tipoPrueba=RM&desde=2026-12-01T09:00&hasta=2026-12-01T13:00&duracionMinutos=30
Authorization: Bearer <token>
```

La identidad del médico se obtiene del token, no de parámetros enviados por el cliente. `desde` y `hasta` delimitan el intervalo de trabajo consultado y deben corresponder al mismo día. La respuesta es una lista JSON de intervalos disponibles, con el tipo de prueba solicitado.

Las citas asociadas al médico bloquean intervalos de la duración indicada; las citas canceladas no bloquean huecos. Para asociar una cita a un médico se utiliza el constructor de `Cita` que recibe `medicoId`. Las citas antiguas creadas sin esa asociación no se atribuyen a ninguna agenda.