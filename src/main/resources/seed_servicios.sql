-- Seed: 12 servicios demo para barberia-demo
-- Ejecutar solo si no hay servicios: SELECT COUNT(*) FROM servicios WHERE tenant_id = 'barberia-demo';
-- Imágenes: Unsplash (libres de derechos)

INSERT INTO servicios (tenant_id, nombre, descripcion, precio, duracion_minutos, emoji, categoria, imagen_url, imagen_url2, imagen_url3, activo) VALUES

-- ── CORTE ──────────────────────────────────────────────────────────────────
('barberia-demo', 'Corte Clásico',
 'Corte tradicional con máquina y tijera. Incluye lavado y secado.',
 15.00, 30, '✂️', 'Corte',
 'https://images.unsplash.com/photo-1605497788044-5a32c7078486?w=800&q=80',
 'https://images.unsplash.com/photo-1657105052497-f996284ffff8?w=800&q=80',
 'https://images.unsplash.com/photo-1635273051937-a0ddef9573b6?w=800&q=80',
 true),

('barberia-demo', 'Fade / Degradado',
 'Degradado moderno con máquina, desde piel o bajo. Diseño de línea incluido.',
 22.00, 45, '✂️', 'Corte',
 'https://images.unsplash.com/photo-1657105052497-f996284ffff8?w=800&q=80',
 'https://images.unsplash.com/photo-1635273051937-a0ddef9573b6?w=800&q=80',
 'https://images.unsplash.com/photo-1605497788044-5a32c7078486?w=800&q=80',
 true),

('barberia-demo', 'Corte + Diseño',
 'Corte personalizado con diseño geométrico o de iniciales a cargo del maestro barbero.',
 28.00, 50, '✂️', 'Corte',
 'https://images.unsplash.com/photo-1635273051937-a0ddef9573b6?w=800&q=80',
 'https://images.unsplash.com/photo-1605497788044-5a32c7078486?w=800&q=80',
 'https://images.unsplash.com/photo-1657105052497-f996284ffff8?w=800&q=80',
 true),

-- ── BARBA ──────────────────────────────────────────────────────────────────
('barberia-demo', 'Arreglo de Barba',
 'Perfilado y recorte de barba con tijera y navaja. Hidratación incluida.',
 12.00, 20, '🪒', 'Barba',
 'https://images.unsplash.com/photo-1517832606299-7ae9b720a186?w=800&q=80',
 'https://images.unsplash.com/photo-1599011176306-4a96f1516d4d?w=800&q=80',
 'https://images.unsplash.com/photo-1705976062088-5433328c2dcd?w=800&q=80',
 true),

('barberia-demo', 'Afeitado Clásico',
 'Afeitado completo con navaja, toalla caliente, espuma artesanal y loción aftershave.',
 18.00, 30, '🪒', 'Barba',
 'https://images.unsplash.com/photo-1599011176306-4a96f1516d4d?w=800&q=80',
 'https://images.unsplash.com/photo-1705976062088-5433328c2dcd?w=800&q=80',
 'https://images.unsplash.com/photo-1517832606299-7ae9b720a186?w=800&q=80',
 true),

('barberia-demo', 'Diseño de Barba',
 'Modelado y diseño de barba a medida según la morfología del rostro. Acabado premium.',
 22.00, 35, '🪒', 'Barba',
 'https://images.unsplash.com/photo-1705976062088-5433328c2dcd?w=800&q=80',
 'https://images.unsplash.com/photo-1517832606299-7ae9b720a186?w=800&q=80',
 'https://images.unsplash.com/photo-1599011176306-4a96f1516d4d?w=800&q=80',
 true),

-- ── COMBO ──────────────────────────────────────────────────────────────────
('barberia-demo', 'Combo Corte + Barba',
 'El combo más popular. Corte a elección + arreglo de barba completo.',
 25.00, 55, '💈', 'Combo',
 'https://images.unsplash.com/photo-1630827020718-3433092696e7?w=800&q=80',
 'https://images.unsplash.com/photo-1647140655214-e4a2d914971f?w=800&q=80',
 'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?w=800&q=80',
 true),

('barberia-demo', 'Combo Premium',
 'Corte + arreglo de barba + lavado con champú profesional y secado con productos.',
 35.00, 70, '💈', 'Combo',
 'https://images.unsplash.com/photo-1647140655214-e4a2d914971f?w=800&q=80',
 'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?w=800&q=80',
 'https://images.unsplash.com/photo-1630827020718-3433092696e7?w=800&q=80',
 true),

('barberia-demo', 'Combo VIP',
 'Experiencia completa: corte + afeitado con navaja + tratamiento capilar + masaje de cuero cabelludo.',
 50.00, 90, '💈', 'Combo',
 'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?w=800&q=80',
 'https://images.unsplash.com/photo-1630827020718-3433092696e7?w=800&q=80',
 'https://images.unsplash.com/photo-1647140655214-e4a2d914971f?w=800&q=80',
 true),

-- ── COLORACIÓN ─────────────────────────────────────────────────────────────
('barberia-demo', 'Coloración Completa',
 'Tintura completa de raíz a punta. Incluye consulta de tono, aplicación y sellado del color.',
 55.00, 90, '🎨', 'Coloración',
 'https://images.unsplash.com/photo-1470259078422-826894b933aa?w=800&q=80',
 'https://images.unsplash.com/photo-1617391654484-2894196c2cc9?w=800&q=80',
 'https://images.unsplash.com/photo-1660144689256-c9a4a4ac116c?w=800&q=80',
 true),

('barberia-demo', 'Mechas y Reflejos',
 'Técnica de luces parciales: balayage, babylights o mechas clásicas. Brillo y movimiento natural.',
 70.00, 120, '🎨', 'Coloración',
 'https://images.unsplash.com/photo-1617391654484-2894196c2cc9?w=800&q=80',
 'https://images.unsplash.com/photo-1660144689256-c9a4a4ac116c?w=800&q=80',
 'https://images.unsplash.com/photo-1470259078422-826894b933aa?w=800&q=80',
 true),

('barberia-demo', 'Decoloración',
 'Aclarado total con protección capilar intensiva. Ideal para cambios radicales de look.',
 85.00, 120, '🎨', 'Coloración',
 'https://images.unsplash.com/photo-1660144689256-c9a4a4ac116c?w=800&q=80',
 'https://images.unsplash.com/photo-1470259078422-826894b933aa?w=800&q=80',
 'https://images.unsplash.com/photo-1617391654484-2894196c2cc9?w=800&q=80',
 true);
