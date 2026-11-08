-- V4: índice para el tablero (add-hours-dashboard, design D4 / tarea 2.7).
-- La consulta del Socio filtra solo por rango de work_date y status: sin este índice el plan es un
-- escaneo completo de time_entries (verificado con EXPLAIN en DashboardReadAdapterIT). La del
-- Abogado ya usa ix_time_entries_lawyer_work_date (V3).
CREATE INDEX ix_time_entries_work_date_status ON time_entries (work_date, status);
