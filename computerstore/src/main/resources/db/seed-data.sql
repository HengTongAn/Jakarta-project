-- ============================================================
-- Computer Store Management System - Seed Data
-- Passwords (BCrypt hashes):
--   admin    / admin123
--   customer / customer123
-- ============================================================

USE computer_store;

-- ------------------------------------------------------------
-- Users
-- ------------------------------------------------------------
INSERT INTO users (username, password_hash, full_name, email, role) VALUES
('admin',    '$2a$10$wFpy16sDQ71Ii0tk1iI1Fuxos.zZCLL0LxR4zlKNgvK4OM7t8WvvC', 'System Administrator', 'admin@computershop.test',  'ADMIN'),
('customer', '$2a$10$8e/69CXUT5aI2LTe6XPf6OqWxYfiPa2y19yB3sSkiX.MMX4bSNDnG', 'Jane Customer',        'jane@computershop.test',   'CUSTOMER');

-- ------------------------------------------------------------
-- Categories
-- ------------------------------------------------------------
INSERT INTO categories (name, description) VALUES
('Laptops',           'Portable computers for work, study and gaming'),
('Desktop Computers', 'Full tower and compact desktop computers'),
('Processors (CPU)',  'Central processing units'),
('Graphics Cards',    'Discrete GPUs for gaming and creative work'),
('Motherboards',      'Mainboards for Intel and AMD platforms'),
('Memory (RAM)',      'System memory modules'),
('Storage',           'SSDs and HDDs'),
('Monitors',          'Display panels'),
('Keyboards',         'Wired and wireless keyboards'),
('Mouse',             'Wired and wireless mice'),
('Headsets',          'Audio headsets and headphones'),
('Power Supplies',    'ATX PSU units'),
('Cases',             'Computer chassis'),
('Networking',        'Routers, adapters and networking gear'),
('Accessories',       'Cables, pads and general accessories');

-- ------------------------------------------------------------
-- Brands
-- ------------------------------------------------------------
INSERT INTO brands (name, description) VALUES
('Intel',    'Leading x86 CPU manufacturer'),
('AMD',      'CPU and GPU manufacturer'),
('NVIDIA',   'Graphics processing unit designer'),
('ASUS',     'Motherboards, laptops and components'),
('MSI',      'Gaming hardware and components'),
('Acer',     'Laptops and monitors'),
('Dell',     'Laptops and desktops'),
('HP',       'Laptops, desktops and peripherals'),
('Lenovo',   'Laptops and desktops'),
('Samsung',  'Memory, storage and monitors'),
('Kingston', 'Memory and storage'),
('Corsair',  'PSUs, memory and peripherals'),
('Logitech', 'Peripherals and accessories'),
('Razer',    'Gaming peripherals'),
('Western Digital', 'Storage drives');

-- ------------------------------------------------------------
-- Products (status is recalculated by the application from stock)
-- ------------------------------------------------------------
INSERT INTO products (category_id, brand_id, name, sku, description, price, stock_quantity) VALUES
(1,  3, 'ASUS ROG Zephyrus G14 Gaming Laptop', 'LAP-ASUS-G14',   '14" Ryzen 9, RTX 4060, 16GB RAM, 1TB SSD. Perfect portable gaming machine.',   1299.00, 12),
(1,  6, 'Acer Swift 3 Ultrabook',              'LAP-ACER-SW3',   '14" FHD, Intel Core i5, 8GB RAM, 512GB SSD. Lightweight daily driver.',           699.00,  8),
(1,  8, 'HP Pavilion 15',                      'LAP-HP-PAV15',   '15.6" FHD, Core i7, 16GB RAM, 512GB SSD. Great all-rounder for home and office.', 899.00,  0),
(1,  9, 'Lenovo ThinkPad X1 Carbon',           'LAP-LNV-X1C',    '14" WUXGA, Core i7, 16GB RAM, 1TB SSD. Business flagship laptop.',                1599.00, 3),
(2,  8, 'HP Omen 30L Gaming Desktop',          'DESK-HP-O30',    'Core i7, RTX 4070, 32GB RAM, 2TB SSD. Ready-to-play gaming tower.',               1999.00, 5),
(2,  7, 'Dell OptiPlex 7010 Tower',            'DESK-DELL-7010', 'Core i5, 16GB RAM, 512GB SSD. Reliable office desktop.',                          849.00,  10),
(3,  1, 'Intel Core i7-14700K',                'CPU-INTEL-1470', '20-core unlocked desktop processor, LGA1700.',                                    429.00,  15),
(3,  1, 'Intel Core i5-13400F',                'CPU-INTEL-1340', '10-core value desktop processor, LGA1700, no iGPU.',                              199.00,  25),
(3,  2, 'AMD Ryzen 7 7800X3D',                 'CPU-AMD-7800X3D','8-core gaming processor with 3D V-Cache, AM5.',                                   349.00,  9),
(4,  3, 'NVIDIA RTX 4070 Super 12GB',          'GPU-NV-4070S',   '12GB GDDR6X graphics card with ray tracing and DLSS 3.',                         579.00,  6),
(4,  3, 'NVIDIA RTX 4060 8GB',                 'GPU-NV-4060',    '8GB GDDR6 graphics card, great 1080p performer.',                                299.00,  18),
(5,  4, 'ASUS ROG Strix B760-F',               'MBD-ASUS-B760',  'Intel LGA1700 ATX motherboard with Wi-Fi 6E.',                                   199.00,  0),
(5,  5, 'MSI MAG B650 Tomahawk',               'MBD-MSI-B650',   'AMD AM5 ATX motherboard, DDR5 and PCIe 5.0 ready.',                              189.00,  14),
(6, 10, 'Samsung 32GB DDR5 RAM Kit',           'RAM-SAM-32GB',   '2x16GB DDR5 5600MHz kit, dual-channel.',                                        109.00,  20),
(6, 11, 'Kingston Fury 16GB DDR4',             'RAM-KIN-16GB',   '2x8GB DDR4 3200MHz gaming memory kit.',                                         49.99,  3),
(7, 10, 'Samsung 980 Pro 1TB NVMe SSD',        'SSD-SAM-1TB',    'PCIe 4.0 NVMe M.2 SSD, 7000MB/s read.',                                         119.00,  22),
(7, 12, 'Corsair MP600 2TB NVMe SSD',          'SSD-COR-2TB',    'PCIe 4.0 NVMe M.2 SSD, huge storage for games.',                                 189.00,  7),
(7, 15, 'WD Blue 4TB HDD',                     'HDD-WD-4TB',     '3.5" 5400RPM SATA HDD for bulk storage.',                                        99.99,  11),
(8, 10, 'Samsung Odyssey G5 27" Monitor',      'MON-SAM-G5',     '27" QHD 144Hz curved gaming monitor.',                                           299.00,  4),
(8,  4, 'ASUS TUF Gaming VG27AQ1A',            'MON-ASUS-VG27',  '27" QHD 170Hz gaming monitor with G-Sync compatible.',                           269.00,  16),
(9,  4, 'ASUS ROG Falchion 65% Keyboard',      'KBD-ASUS-FAL',   'Compact 65% wireless mechanical gaming keyboard.',                               89.99,  2),
(9, 14, 'Razer BlackWidow V4 Mechanical',      'KBD-RAZER-BW4',  'Full-size mechanical gaming keyboard with Razer Green switches.',                89.99,  13),
(10, 13, 'Logitech G502 Hero Gaming Mouse',    'MSE-LOG-G502',   'High-precision 25K DPI gaming mouse with RGB.',                                  39.99,  30),
(10, 14, 'Razer DeathAdder V2',                'MSE-RAZER-DA2',  'Ergonomic optical gaming mouse, 20K DPI.',                                       29.99,  17),
(11, 14, 'Razer BlackShark V2 Pro',            'HST-RAZER-BSV2', 'Wireless gaming headset with THX spatial audio.',                                99.99,  5),
(11, 4,  'ASUS ROG Delta S',                   'HST-ASUS-DS',    'Hi-fi gaming headset with USB-C, ESS QUAD-DAC.',                                 69.99,  0),
(12, 12, 'Corsair RM850x 850W PSU',            'PSU-COR-RM850',  '80+ Gold fully modular ATX power supply.',                                      149.99,  9),
(12, 12, 'Corsair RM1000x 1000W PSU',          'PSU-COR-RM1000', '80+ Gold fully modular high-wattage PSU.',                                       199.99,  15),
(13, 12, 'Corsair 4000D Airflow Case',         'CASE-COR-4000D', 'Mid-tower ATX case with excellent airflow.',                                     94.99,  12),
(13, 5,  'MSI MAG Forge 100R Case',            'CASE-MSI-100R',  'Mid-tower gaming case with tempered glass.',                                     59.99,  6),
(14, 4,  'ASUS RT-AX55 Wi-Fi 6 Router',        'NET-ASUS-AX55',  'AX1800 dual-band Wi-Fi 6 router.',                                              79.99,  1),
(14, 9,  'Lenovo USB-C Dock Gen2',             'NET-LNV-DOCK',   'USB-C dock with HDMI, DisplayPort and gigabit LAN.',                             129.99,  8),
(15, 13, 'Logitech C920 Pro Webcam',           'ACC-LOG-C920',   'Full HD 1080p webcam for streaming and calls.',                                  59.99,  14),
(15, 13, 'Logitech Desk Mat (Large)',          'ACC-LOG-MAT',    'Large cloth desk mat, 90x40cm.',                                                 19.99,  40);