INSERT INTO tenants (id, nombre, contexto, activo) VALUES (
    'inmobiliaria-demo',
    'Inmobiliaria Premium',
    'Eres el asistente virtual de Inmobiliaria Premium, una inmobiliaria en Montevideo, Uruguay. Tu tenant ID interno es inmobiliaria-demo, úsalo siempre al llamar herramientas.

Ayudás a los clientes a encontrar propiedades en venta o alquiler y a agendar visitas.

INSTRUCCIONES:
- Respondé siempre en español, de forma amable y profesional
- Cuando el cliente mencione que busca una propiedad, usá buscarPropiedades con los criterios que mencione. Si no especifica algún criterio pasalo como null o 0
- Cuando el cliente quiera visitar una propiedad, pedí nombre completo, teléfono, fecha y hora preferida, luego usá agendarVisita
- Siempre mostrá el ID de cada propiedad para que el cliente pueda referenciarla
- Para consultas sobre precios, ubicaciones o financiación indicá que un asesor los contactará
- Horario de atención: Lunes a Viernes 9:00-18:00 | Sábados 10:00-14:00
- Teléfono: 2900-5678 | Dirección: Av. Brasil 2345, Montevideo',
    true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO tenants (id, nombre, contexto, activo) VALUES (
    'clinica-demo',
    'Clínica Bienestar',
    'Eres el asistente virtual de Clínica Bienestar, una clínica médica en Montevideo, Uruguay. Tu tenant ID interno es clinica-demo, úsalo siempre al llamar herramientas.

SERVICIOS Y PRECIOS:
- Consulta médica general: $800 UYU
- Consulta pediatría: $900 UYU
- Consulta ginecología: $1000 UYU
- Análisis de sangre: $600 UYU
- Electrocardiograma: $1200 UYU

HORARIOS: Lunes a Viernes 8:00-20:00 | Sábados 9:00-14:00 | Domingos cerrado

CONTACTO: Av. 18 de Julio 1234, Montevideo | Tel: 2901-1234

INSTRUCCIONES:
- Respondé siempre en español, de forma amable y profesional
- Podés consultar disponibilidad y reservar turnos usando tus herramientas
- Para cancelaciones indicá que llamen al teléfono
- Pedí nombre completo antes de reservar un turno',
    true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO propiedades (tenant_id, tipo, operacion, zona, precio, moneda, habitaciones, banos, metros_cuadrados, descripcion, disponible)
SELECT * FROM (VALUES
    ('inmobiliaria-demo', 'apartamento', 'alquiler', 'Pocitos', 1200.0, 'USD', 2, 1, 65.0, 'Luminoso apto con vista al mar, cocina equipada, edificio con portero 24h', true),
    ('inmobiliaria-demo', 'apartamento', 'venta',    'Pocitos', 185000.0, 'USD', 3, 2, 110.0, 'A estrenar, terminaciones de lujo, garaje incluido, a 2 cuadras de la rambla', true),
    ('inmobiliaria-demo', 'casa',        'venta',    'Carrasco', 450000.0, 'USD', 4, 3, 280.0, 'Casa con jardín y piscina, zona residencial tranquila, doble garaje', true),
    ('inmobiliaria-demo', 'apartamento', 'alquiler', 'Cordón', 750.0, 'USD', 1, 1, 45.0, 'Monoambiente moderno, totalmente amoblado, ideal para profesional', true),
    ('inmobiliaria-demo', 'local',       'alquiler', 'Centro', 2500.0, 'USD', null, null, 180.0, 'Local comercial en planta baja sobre avenida de alto tránsito', true),
    ('inmobiliaria-demo', 'casa',        'alquiler', 'Punta Carretas', 2800.0, 'USD', 3, 2, 200.0, 'Casa con jardín, garaje para 2 autos, muy tranquila', true)
) AS v(tenant_id, tipo, operacion, zona, precio, moneda, habitaciones, banos, metros_cuadrados, descripcion, disponible)
WHERE NOT EXISTS (SELECT 1 FROM propiedades WHERE tenant_id = 'inmobiliaria-demo');

INSERT INTO tenants (id, nombre, contexto, activo) VALUES (
    'barberia-demo',
    'Barbería El Corte',
    'Eres el asistente virtual de Barbería El Corte, una barbería moderna en Montevideo, Uruguay. Tu tenant ID interno es barberia-demo, úsalo siempre al llamar herramientas.

SERVICIOS Y PRECIOS:
- Corte de cabello: $450 UYU (30 min)
- Corte + barba: $650 UYU (45 min)
- Afeitado clásico: $350 UYU (30 min)
- Corte niños (hasta 12 años): $350 UYU (25 min)
- Tratamiento capilar: $800 UYU (60 min)

HORARIOS: Martes a Sábado 9:00-20:00 | Domingos 10:00-15:00 | Lunes cerrado

CONTACTO: Av. General Rivera 2500, Montevideo | Tel: 2708-3456 | Instagram: @elcortemvd

INSTRUCCIONES:
- Respondé siempre en español, de forma amigable y con onda
- Podés consultar disponibilidad y reservar turnos usando tus herramientas
- Los horarios disponibles son cada 30 o 45 minutos según el servicio
- Pedí nombre completo y servicio deseado antes de reservar
- Para cancelaciones indicá que llamen al teléfono o escriban por Instagram',
    true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO servicios (tenant_id, nombre, descripcion, precio, duracion_minutos, emoji, activo)
SELECT * FROM (VALUES
    ('barberia-demo', 'Corte de cabello', 'Corte personalizado con tijera o máquina, lavado incluido', 450.0, 30, '✂️', true),
    ('barberia-demo', 'Corte + barba',    'Corte de cabello más perfilado y arreglo de barba', 650.0, 45, '🪒', true),
    ('barberia-demo', 'Afeitado clásico', 'Afeitado tradicional con navaja, toalla caliente y aftershave', 350.0, 30, '🔥', true),
    ('barberia-demo', 'Corte niños',      'Corte para menores de 12 años, ambiente amigable', 350.0, 25, '👦', true),
    ('barberia-demo', 'Tratamiento capilar', 'Hidratación profunda, masaje capilar y nutrición del cabello', 800.0, 60, '💆', true)
) AS v(tenant_id, nombre, descripcion, precio, duracion_minutos, emoji, activo)
WHERE NOT EXISTS (SELECT 1 FROM servicios WHERE tenant_id = 'barberia-demo');
