-- Seed users (password is BCrypt hash for 'password')
INSERT INTO users (username, password, role) 
VALUES ('admin', '$2a$10$wN1G.xI76V82d9/1x831.uKSpZ6Qc9b7YJ52V1u957j18tF/YQ4Gq', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- Seed data sources
INSERT INTO data_sources (source_id, source_name, source_type, owner) VALUES
('sales-data', 'Sales Transactions', 'sales', 'sales-team'),
('customer-data', 'Customer Profiles', 'customer', 'crm-team'),
('sensor-data', 'IoT Sensor Readings', 'sensor', 'iot-team'),
('payment-data', 'Payment Transactions', 'payment', 'finance-team'),
('inventory-data', 'Warehouse Inventory', 'inventory', 'logistics-team')
ON CONFLICT (source_id) DO NOTHING;

-- Seed validation rules for sales-data
INSERT INTO validation_rules (source_id, rule_name, rule_type, field_name, threshold, severity, enabled) VALUES
('sales-data', 'Require Transaction ID', 'REQUIRED_FIELD', 'transactionId', NULL, 'CRITICAL', TRUE),
('sales-data', 'Require Customer ID', 'REQUIRED_FIELD', 'customerId', NULL, 'ERROR', TRUE),
('sales-data', 'Validate Amount Type', 'INVALID_TYPE', 'amount', NULL, 'CRITICAL', TRUE),
('sales-data', 'Positive Amount Check', 'CUSTOM_CHECK', 'amount', 0.0, 'ERROR', TRUE),
('sales-data', 'Transaction Amount Outlier', 'OUTLIER', 'amount', 3.0, 'WARNING', TRUE),
('sales-data', 'Sales Drift Detection', 'DRIFT', 'amount', 0.2, 'WARNING', TRUE)
ON CONFLICT DO NOTHING;

-- Seed validation rules for customer-data
INSERT INTO validation_rules (source_id, rule_name, rule_type, field_name, threshold, severity, enabled) VALUES
('customer-data', 'Require Customer ID', 'REQUIRED_FIELD', 'customerId', NULL, 'CRITICAL', TRUE),
('customer-data', 'Valid Email Format', 'INVALID_EMAIL', 'email', NULL, 'ERROR', TRUE),
('customer-data', 'Valid Phone Format', 'INVALID_PHONE', 'phone', NULL, 'WARNING', TRUE)
ON CONFLICT DO NOTHING;

-- Seed validation rules for sensor-data
INSERT INTO validation_rules (source_id, rule_name, rule_type, field_name, threshold, severity, enabled) VALUES
('sensor-data', 'Require Sensor ID', 'REQUIRED_FIELD', 'sensorId', NULL, 'CRITICAL', TRUE),
('sensor-data', 'Temperature Upper Bound', 'CUSTOM_CHECK', 'temperature', 100.0, 'ERROR', TRUE),
('sensor-data', 'Temperature Outlier Check', 'OUTLIER', 'temperature', 3.0, 'WARNING', TRUE)
ON CONFLICT DO NOTHING;

-- Seed validation rules for payment-data
INSERT INTO validation_rules (source_id, rule_name, rule_type, field_name, threshold, severity, enabled) VALUES
('payment-data', 'Require Payment ID', 'REQUIRED_FIELD', 'paymentId', NULL, 'CRITICAL', TRUE),
('payment-data', 'Valid Payment Amount', 'CUSTOM_CHECK', 'amount', 0.0, 'CRITICAL', TRUE)
ON CONFLICT DO NOTHING;

-- Seed validation rules for inventory-data
INSERT INTO validation_rules (source_id, rule_name, rule_type, field_name, threshold, severity, enabled) VALUES
('inventory-data', 'Require Product ID', 'REQUIRED_FIELD', 'productId', NULL, 'CRITICAL', TRUE),
('inventory-data', 'Quantity Non-Negative', 'CUSTOM_CHECK', 'quantity', 0.0, 'ERROR', TRUE)
ON CONFLICT DO NOTHING;
