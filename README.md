# ISO26-ISO2-3B1.01

## Consulta de disponibilidad de agendas

El personal médico puede consultar los huecos libres de su agenda diaria mediante:

```text
GET /api/agendas/disponibilidad?tipoPrueba=RM&desde=2026-12-01T09:00&hasta=2026-12-01T13:00&duracionMinutos=30
Authorization: Bearer <token>
```

La identidad del médico se obtiene del token, no de parámetros enviados por el cliente. `desde` y `hasta` delimitan el intervalo de trabajo consultado y deben corresponder al mismo día. La respuesta es una lista JSON de intervalos disponibles, con el tipo de prueba solicitado.

Las citas asociadas al médico bloquean intervalos de la duración indicada; las citas canceladas no bloquean huecos. Para asociar una cita a un médico se utiliza el constructor de `Cita` que recibe `medicoId`. Las citas antiguas creadas sin esa asociación no se atribuyen a ninguna agenda.

## CDU11: informes de pruebas

- `POST /api/informes-prueba`: un médico crea el informe de una prueba realizada, con `pruebaId`, `hallazgos` y `conclusion` en JSON. La fecha de creación la establece el servidor.
- `GET /api/informes-prueba`: el paciente autenticado consulta sus informes.
- `GET /api/informes-prueba/medico`: el médico autenticado consulta los informes que ha creado.
- `GET /api/informes-prueba/{id}`: consulta el detalle si la identidad autenticada es el paciente propietario o el médico autor.
- `POST /api/informes-prueba/{id}/imagen`: el médico autor adjunta una imagen opcional mediante los campos JSON `tipoImagen` (`image/png` o `image/jpeg`) e `imagenBase64`.
- `GET /api/informes-prueba/{id}/imagen`: el paciente propietario o el médico autor descarga la imagen.

Las imágenes se almacenan en memoria, tienen un tamaño máximo de 5 MiB y se comprueba que la firma binaria coincida con PNG o JPEG. El repositorio en memoria no conserva los informes ni las imágenes al reiniciar el proceso.