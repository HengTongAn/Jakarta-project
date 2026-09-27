-- ============================================================
-- Computer Store Management System - Seed Data
-- DEV-ONLY: these credentials are public knowledge. Never run this
-- file against a reachable deployment. The app logs a loud ERROR at
-- startup while a seeded account is still present (see
-- util/DefaultCredentialsChecker) and the go-live checklist requires
-- changing both accounts before the store is reachable.
-- Passwords (BCrypt hashes):
--   admin    / admin123   (SUPER_ADMIN - the store owner)
--   customer / customer123
-- ============================================================

USE computer_store;

-- ------------------------------------------------------------
-- Users
-- ------------------------------------------------------------
INSERT INTO users (username, password_hash, full_name, email, role) VALUES
('admin',    '$2a$10$wFpy16sDQ71Ii0tk1iI1Fuxos.zZCLL0LxR4zlKNgvK4OM7t8WvvC', 'System Administrator', 'admin@computershop.test',  'SUPER_ADMIN'),
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
(1,  4, 'ASUS ROG Zephyrus G14 Gaming Laptop', 'LAP-ASUS-G14',   '14" Ryzen 9, RTX 4060, 16GB RAM, 1TB SSD. Perfect portable gaming machine.',   1299.00, 12),
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

-- ------------------------------------------------------------
-- Products, part 2: the generated catalogue that brings the store
-- to 150 items (ten per category). status is recalculated below
-- from stock_quantity, exactly as for the rows above.
--
-- image_url is intentionally omitted, as it is for the rows above:
-- uploads live outside the webapp in ~/.computerstore/uploads/
-- product-images (see util/file/UploadConfig) and are not part of the
-- repository. Seeded rows therefore render the built-in placeholder
-- icon until a picture is uploaded for them via the admin product
-- form.
-- ------------------------------------------------------------
INSERT INTO products (category_id, brand_id, name, sku, description, price, stock_quantity) VALUES
(1, 4, 'ASUS Zenbook 14 OLED', 'LAP-ASUS-ZENBOOK1', '14" 2.8K OLED, Core Ultra 7, 16GB LPDDR5, 1TB SSD. 1.1kg all-day battery.', 1099.00, 6),
(1, 5, 'MSI Katana 15 Gaming Laptop', 'LAP-MSI-KATANA15', '15.6" 144Hz FHD, Core i7, RTX 4050, 16GB RAM, 512GB SSD.', 1149.00, 4),
(1, 7, 'Dell XPS 15 Developer Edition', 'LAP-DELL-XPS15', '15.6" 3.5K OLED, Core i9, 32GB RAM, 1TB SSD. Tuned for compiling and containers.', 1899.00, 3),
(1, 8, 'HP Spectre x360 14', 'LAP-HP-SPECTREX', '14" 3K2K touch, Core Ultra 5, 16GB RAM, 512GB SSD. 2-in-1 convertible.', 1349.00, 5),
(1, 9, 'Lenovo IdeaPad Pro 5 16', 'LAP-LENO-IDEAPADP', '16" 2.5K, Ryzen 7, 16GB RAM, 1TB SSD. Big screen, big battery.', 999.00, 7),
(2, 4, 'ASUS ProArt Station PD5', 'DESK-ASUS-PROARTST', 'Xeon W-3500, RTX 4000 Ada, 64GB ECC RAM, 2TB NVMe. Rack-ready render box.', 3299.00, 2),
(2, 5, 'MSI Infinite 2 Gaming Desktop', 'DESK-MSI-INFINITE', 'Core i7, RTX 4070 Ti, 32GB DDR5, 1TB NVMe. Compact 13L gaming tower.', 1599.00, 5),
(2, 5, 'MSI Creator 24', 'DESK-MSI-CREATOR2', 'Ryzen 9, RTX 4060, 32GB RAM, 1TB SSD. Studio-focused mini workstation.', 1299.00, 4),
(2, 6, 'Acer Predator Orion 3000', 'DESK-ACER-PREDATOR', 'Core i7, RTX 4080 SUPER, 32GB DDR5, 2TB NVMe. Liquid-cooled gaming desktop.', 1799.00, 3),
(2, 7, 'Dell Precision 3660 Tower', 'DESK-DELL-PRECISIO', 'Core i9, RTX 2000 Ada, 64GB ECC, 2TB NVMe. Entry professional workstation.', 2399.00, 2),
(2, 8, 'HP Z2 Tower G9 Workstation', 'DESK-HP-Z2TOWER', 'Xeon E-2488, RTX A2000, 64GB ECC, 2TB NVMe. ISV-certified CAD workstation.', 2799.00, 2),
(2, 9, 'Lenovo ThinkStation P3 Tower', 'DESK-LENO-THINKSTA', 'Core i7, RTX 4000 Ada, 32GB ECC, 1TB NVMe. Manageable pro workstation.', 1899.00, 4),
(2, 8, 'HP Omen 40L Desktop', 'DESK-HP-OMEN40L', 'Core i7, RTX 4070, 32GB DDR5, 1TB NVMe. Quiet-cooled gaming desktop.', 1499.00, 6),
(3, 1, 'Intel Core i5-14600K', 'CPU-INTEL-I5-14600K', '14 cores (6P+8E), up to 5.3GHz, unlocked. The mainstream gaming sweet spot.', 319.00, 22),
(3, 1, 'Intel Core i9-14900K', 'CPU-INTEL-I9-14900K', '24 cores (8P+16E), up to 6.0GHz. Top-bin Intel flagship for heavy multitask.', 549.00, 8),
(3, 1, 'Intel Core i5-12400F', 'CPU-INTEL-I5-12400F', '6 cores, up to 4.4GHz, no iGPU. Cheapest sane 1080p/1440p gaming build.', 179.00, 31),
(3, 2, 'AMD Ryzen 5 7600X', 'CPU-AMD-R5-7600X', '6C/12T Zen 4, up to 5.3GHz, AM5. Excellent 1080p and 1440p value pick.', 229.00, 26),
(3, 2, 'AMD Ryzen 9 7950X3D', 'CPU-AMD-R9-7950X3D', '16C/32T Zen 4, dual 3D V-Cache. Best-in-class gaming and compiling combined.', 699.00, 5),
(4, 3, 'NVIDIA GeForce RTX 4090 Founders', 'GPU-NVIDIA-RTX4090', '24GB GDDR6X, 1008-bit, DLSS 3. No raster GPU faster for gaming and AI.', 1899.00, 2),
(4, 3, 'NVIDIA GeForce RTX 4070 Ti SUPER', 'GPU-NVIDIA-RTX4070TI-SUPER', '16GB GDDR6X, DLSS 3. Superb 1440p high-refresh raster and ray tracing.', 849.00, 6),
(4, 3, 'NVIDIA GeForce RTX 4060 Ti', 'GPU-NVIDIA-RTX4060TI', '16GB GDDR6, DLSS 3. 1080p ultra plus comfortable 1440p in most titles.', 429.00, 15),
(4, 3, 'NVIDIA GeForce RTX 4060', 'GPU-NVIDIA-RTX4060', '8GB GDDR6, DLSS 3. The most popular 1080p card on the market.', 299.00, 24),
(4, 4, 'ASUS TUF Gaming GeForce RTX 4070', 'GPU-ASUS-TUFGAMIN', '12GB GDDR6X, 3-slot thermal design, DLSS 3. Factory-occluded 4070.', 679.00, 7),
(4, 4, 'ASUS ROG Strix GeForce RTX 4080 SUPER', 'GPU-ASUS-ROGSTRIX', '16GB GDDR6X, triple-fan, DLSS 3. Premium factory OC with Aura lighting.', 1399.00, 3),
(4, 5, 'MSI Gaming X Trio GeForce RTX 4070 Ti', 'GPU-MSI-GAMINGX', '12GB GDDR6X, triple-fan, DLSS 3. Ice-blade-fan 4070 Ti variant.', 829.00, 5),
(4, 5, 'MSI Ventus 2X GeForce RTX 4060 Ti', 'GPU-MSI-VENTUS2X', '8GB GDDR6, dual-fan, DLSS 3. The cheapest sensible 1440p stepping stone.', 409.00, 12),
(5, 4, 'ASUS ROG Maximus Z890 Hero', 'MBD-ASUS-ROGMAXIM', 'LGA1851, Z890, DDR5, Wi-Fi 7, 5x M.2. Enthusiast Intel flagship board.', 699.00, 4),
(5, 4, 'ASUS TUF Gaming B650-Plus WiFi', 'MBD-ASUS-TUFGAMIN', 'AM5, B650, DDR5, Wi-Fi 6E, 3x M.2. Durable mid-range Ryzen board.', 219.00, 16),
(5, 4, 'ASUS ROG Strix X670E-I Gaming WiFi', 'MBD-ASUS-ROGSTRIX', 'AM5, X670E, DDR5, Wi-Fi 6E, 4x M.2. Mini-ITEX high-end Ryzen board.', 449.00, 6),
(5, 4, 'ASUS Prime B760M-A WiFi', 'MBD-ASUS-PRIMEB76', 'LGA1700, B760, DDR5, Wi-Fi 6, 2x M.2. Budget mATX for 12th/13th gen.', 159.00, 28),
(5, 5, 'MSI MAG Z890 Tomahawk WiFi', 'MBD-MSI-MAGZ890', 'LGA1851, Z890, DDR5, Wi-Fi 7, 4x M.2. ATX mainstream Intel board.', 279.00, 13),
(5, 5, 'MSI MAG B850 Tomahawk WiFi', 'MBD-MSI-MAGB850', 'AM5, B850, DDR5, Wi-Fi 7, 4x M.2. Future-ready Ryzen 9000 board.', 239.00, 15),
(5, 5, 'MSI PRO B760M-A WiFi', 'MBD-MSI-PROB760M', 'LGA1700, B760, DDR5, Wi-Fi 6E, 2x M.2. Workhorse micro-ATX.', 149.00, 22),
(5, 5, 'MSI MPG X670E Carbon WiFi', 'MBD-MSI-MPGX670E', 'AM5, X670E, DDR5, Wi-Fi 6E, 5x M.2. Carbon-brace high-end ATX.', 449.00, 4),
(6, 11, 'Kingston FURY Beast DDR5 32GB (2x16GB)', 'RAM-KING-FURYBEAS', '6000 MT/s CL36, XMP/EXPO, low-profile RGB-free heatspreader.', 109.00, 30),
(6, 11, 'Kingston FURY Beast DDR5 64GB (2x32GB)', 'RAM-KING-FURYBEAS-2', '5600 MT/s CL40, XMP/EXPO. Capacity for VMs, video and huge datasets.', 219.00, 18),
(6, 11, 'Kingston FURY Renegade DDR5 32GB (2x16GB)', 'RAM-KING-FURYRENE', '7200 MT/s CL36, XMP/EXPO, low-latency aluminium heatspreader.', 159.00, 12),
(6, 11, 'Kingston FURY Impact DDR5 32GB (2x16GB)', 'RAM-KING-FURYIMPA', '5600 MT/s CL40 SODIMM. Laptop SODIMM kit for DDR5 slots.', 129.00, 14),
(6, 11, 'Kingston ValueRAM DDR4 16GB (2x8GB)', 'RAM-KING-VALUERAM', '3200 MT/s CL22. Plain, reliable DDR4 for older builds and upgrades.', 39.00, 48),
(6, 12, 'Corsair Vengeance DDR5 32GB (2x16GB)', 'RAM-CORS-VENGEANC', '6000 MT/s CL30, XMP/EXPO. The fastest low-latency 32GB kit in common use.', 114.00, 26),
(6, 12, 'Corsair Vengeance LPX DDR5 32GB (2x16GB)', 'RAM-CORS-VENGEANC-2', '6000 MT/s CL36, low-profile. Fits under every air cooler and AIO.', 104.00, 20),
(6, 12, 'Corsair Dominator Titanium DDR5 32GB (2x16GB)', 'RAM-CORS-DOMINATO', '6000 MT/s CL30, RGB lighting. Premium binned heatspreader kit.', 189.00, 8),
(7, 10, 'Samsung 990 PRO 2TB NVMe', 'SSD-SAMS-990PRO', '7450 MB/s read, 6900 MB/s write, PCIe 4.0. The reference-class Gen4 drive.', 169.00, 26),
(7, 10, 'Samsung 990 EVO Plus 1TB NVMe', 'SSD-SAMS-990EVO', '7150 MB/s read, PCIe 4.0 x4. Excellent value boot and scratch drive.', 89.00, 33),
(7, 10, 'Samsung 870 EVO 4TB SATA', 'SSD-SAMS-870EVO', '560 MB/s read, 2.5" SATA. Large, dependable bulk storage.', 279.00, 11),
(7, 11, 'Kingston NV3 1TB NVMe', 'SSD-KING-NV31TB', '6000 MB/s read, PCIe 4.0 x4. The cheapest high-volume NVMe option.', 54.00, 52),
(7, 11, 'Kingston DC2000 1.8TB NVMe', 'SSD-KING-DC2000-18T', '7000 MB/s read, endurance-rated. Datacentre-class drive for heavy writes.', 189.00, 7),
(7, 15, 'WD Black SN850X 2TB NVMe', 'SSD-WEST-BLACKSN8', '7300 MB/s read, PCIe 4.0 x4. The gaming NVMe benchmark for three years.', 149.00, 21),
(7, 15, 'WD Blue SA510 SATA 1TB', 'SSD-WEST-BLUESA51', '560 MB/s read, 2.5" SATA. Straightforward secondary storage.', 59.00, 34),
(8, 4, 'ASUS ROG Swift PG27AQDP 27" 1440p', 'MON-ASUS-ROGSWIFT', '27" 2560x1440, 480Hz, Fast IPS, 0.03ms. Elite competitive gaming panel.', 749.00, 5),
(8, 4, 'ASUS ProArt PA279CV 27" 4K', 'MON-ASUS-PROARTPA', '27" 3840x2160 IPS, 100% sRGB, USB-C 65W. Colour-accurate 4K creator monitor.', 549.00, 6),
(8, 6, 'Acer Nitro XV275KQ 27" 4K', 'MON-ACER-NITROXV2', '27" 3840x2160, 240Hz, 0.5ms. Dual-purpose gaming and 4K productivity.', 599.00, 4),
(8, 6, 'Acer Swift 27" 1440p', 'MON-ACER-SWIFT27', '27" 2560x1440, 100Hz IPS, 1ms. Clean everyday office and study panel.', 259.00, 9),
(8, 7, 'Dell UltraSharp U2724D 27"', 'MON-DELL-ULTRASHA', '27" QHD+, 120Hz, IPS Black, 90W USB-C docking. Office monitor with real blacks.', 419.00, 7),
(8, 8, 'HP OMEN 32" QHD 165Hz', 'MON-HP-OMEN32', '32" 2560x1440, 165Hz, 1ms. Big and fast for console and sim racing.', 449.00, 5),
(8, 9, 'Lenovo ThinkVision P27h-30 27"', 'MON-LENO-THINKVIS', '27" 4K IPS, 90W USB-C, 4ms. Tidy all-in-one desk for developers.', 369.00, 8),
(8, 10, 'Samsung Odyssey G7 32" 165Hz', 'MON-SAMS-ODYSSEYG', '32" 1440p, 165Hz, 1ms, curved. Immersive high-refresh curved panel.', 429.00, 6),
(9, 13, 'Logitech MX Keys S Combo', 'KEY-LOGI-MXKEYS', 'Low-profile scissor, backlit, Bolt receiver. Multi-device productivity typing.', 149.00, 14),
(9, 13, 'Logitech G915 TKL', 'KEY-LOGI-G915TKL', 'Low-profile mechanical, LIGHTSYNC, 40h battery. TKL gaming low-profile.', 179.00, 11),
(9, 13, 'Logitech K380 Multi-device', 'KEY-LOGI-K380MULT', 'Compact Bluetooth, 3-device switching. Cheap and genuinely portable.', 39.00, 29),
(9, 13, 'Logitech G213 Prodigal', 'KEY-LOGI-G213PROD', 'Membrane, LIGHTSYNC, spill-resistant. The default first gaming keyboard.', 39.00, 24),
(9, 14, 'Razer BlackWidow V4 Pro', 'KEY-RAZE-BLACKWID', 'Full-size mechanical, Chroma, wrist rest, 8 macros. Feature-packed gaming board.', 229.00, 7),
(9, 14, 'Razer Huntsman V3 Pro', 'KEY-RAZE-HUNTSMAN', 'Full-size analog optical, 8K polling, adjustable actuation. Esports flagship.', 249.00, 6),
(9, 12, 'Corsair K70 RGB Pro', 'KEY-CORS-K70RGB', 'Aluminium top plate, Cherry MX, PBT caps. Long-running mechanical standard.', 169.00, 12),
(9, 12, 'Corsair K65 Plus Wireless', 'KEY-CORS-K65PLUS', '75% wireless mechanical, 275h battery. Small but complete layout.', 129.00, 15),
(10, 13, 'Logitech MX Master 3S', 'MSE-LOGI-MXMASTER', '8K DPI, Quiet clicks, MagSpeed wheel, 3-device. The office productivity king.', 99.00, 22),
(10, 13, 'Logitech G Pro X Superlight 2', 'MSE-LOGI-GPRO', '60g, HERO 2 sensor, 8K polling, 95h. Top-tier esports wireless mouse.', 149.00, 10),
(10, 13, 'Logitech G502 X Lightspeed', 'MSE-LOGI-G502X', '25K DPI sensor, 13 buttons, 89g. Feature-loaded and long-lived.', 79.00, 18),
(10, 13, 'Logitech M185 Silent', 'MSE-LOGI-M185SILE', 'Silent clicks, 1000 DPI, battery lasts a year. The cheap reliable one.', 22.00, 44),
(10, 14, 'Razer DeathAdder V3 Pro', 'MSE-RAZE-DEATHADD', '63g, 30K DPI, Focus Pro sensor, 90h. Featherweight esports shape.', 149.00, 9),
(10, 14, 'Razer Basilisk V3', 'MSE-RAZE-BASILISK', 'Ergonomic right-handed, 30K DPI, 11 buttons. Comfort-first gaming mouse.', 99.00, 13),
(10, 12, 'Corsair M75 Air Wireless', 'MSE-CORS-M75AIR', '57g, 26K DPI, 100h battery. Lightweight claw-grip wireless.', 109.00, 11),
(10, 12, 'Corsair Nightsword Pro', 'MSE-CORS-NIGHTSWO', 'Ergonomic, 26K DPI, 4-zone RGB, thumb rest. Comfort plus lots of buttons.', 139.00, 8),
(11, 14, 'Razer Kraken V3 Pro', 'HST-RAZE-KRAKENV3', '9-mic surround, 50mm drivers, 70h. Wireless esports headset with broadcast mic.', 249.00, 8),
(11, 14, 'Razer BlackShade V2 Pro', 'HST-RAZE-BLACKSHA', '70g, 50mm drivers, detachable boom, THX. Lightweight wireless esports headset.', 179.00, 9),
(11, 13, 'Logitech G Pro X 2', 'HST-LOGI-GPRO', 'HRTF planar drivers, DTS:X, 50h, dual wireless. Premium wireless gaming audio.', 289.00, 7),
(11, 13, 'Logitech G733 Lightspeed', 'HST-LOGI-G733LIGH', 'RGB, 29mm drivers, 24h. The long-running budget wireless headset.', 129.00, 12),
(11, 13, 'Logitech Zone Wireless', 'HST-LOGI-ZONEWIRE', 'Active noise cancelling, certified for meetings. Work-and-game headset.', 149.00, 10),
(11, 4, 'ASUS ROG Delta 4', 'HST-ASUS-ROGDELTA', 'Hi-Res out-of-headband driver, 60mm, dual USB-C. Musician-friendly 2.4G/USB.', 199.00, 6),
(11, 4, 'ASUS TUF Gaming THRUST', 'HST-ASUS-TUFGAMIN', '40mm drivers, detachable mic, cheap. Solid wired value headset.', 79.00, 14),
(11, 12, 'Corsair HS80 Max Wireless', 'HST-CORS-HS80MAX', 'Dolby Atmos, 50mm, 65h, dual wireless. Comfortable multi-platform headset.', 199.00, 8),
(12, 12, 'Corsair RM1000x 1000W 80+ Gold', 'PSU-CORS-RM1000X1', 'Fully modular, ATX 3.0, 12V-2x6 native. Silent 80+ Gold ATX 3.0 flagship.', 189.00, 11),
(12, 12, 'Corsair RM750e 750W 80+ Gold', 'PSU-CORS-RM750E75', 'Fully modular, 80+ Gold, 5-year warranty. The value standard for mid builds.', 109.00, 17),
(12, 12, 'Corsair SF750 Platinum SFX', 'PSU-CORS-SF750PLA', '750W SFX-L, 80+ Platinum, dual 12V-2x6. Small-form-factor high density.', 179.00, 7),
(12, 4, 'ASUS ROG Thor Platinum II 1200W', 'PSU-ASUS-ROGTHOR', '1200W, 80+ Platinum, OLED wattage, ATX 3.0. Extreme-end power for dual GPU rigs.', 399.00, 4),
(12, 4, 'ASUS TUF Gaming 850W Gold', 'PSU-ASUS-TUFGAMIN', '80+ Gold, ATX 3.0, 12VHPWR, 10-year warranty. Tough mid-tower unit.', 139.00, 15),
(12, 4, 'ASUS Prime 650W Gold', 'PSU-ASUS-PRIME650', '80+ Gold, modular cabling, 6-year warranty. Sensible mainstream capacity.', 99.00, 21),
(12, 5, 'MSI MPG A1000G PCIE5', 'PSU-MSI-MPGA1000', '1000W, 80+ Gold, ATX 3.0 native, 10-year warranty. MSI\'s current 1kW option.', 219.00, 9),
(12, 5, 'MSI MAG A750GL PCIe5', 'PSU-MSI-MAGA750G', '750W, 80+ Gold, ATX 3.0, fully modular. Cheap and modern mainstream power.', 99.00, 18),
(13, 4, 'ASUS ROG Hyperion GR701', 'CSE-ASUS-ROGHYPER', 'Full tower, ATX, dual 420mm fans, 2x 12VHPWR. Enormous airflow flagship.', 399.00, 3),
(13, 4, 'ASUS TUF Gaming GT501', 'CSE-ASUS-TUFGAMIN', 'ATX mid tower, mesh front, 3x 140mm fans. Rugged, well-cooled mid tower.', 179.00, 6),
(13, 4, 'ASUS Prime B601 MicroATX', 'CSE-ASUS-PRIMEB60', 'Micro-ATX, 4x GPU slots, 2x fan mounts. Clean small build chassis.', 89.00, 13),
(13, 5, 'MSI MPG G400 Valorant', 'CSE-MSI-MPGG400', 'Micro-ATX, 3x 120mm ARGB fans, 240mm radiator. Affordable gaming case.', 69.00, 19),
(13, 5, 'MSI Omen Siko 300', 'CSE-MSI-OMENSIKO', 'Micro-ATX tempered glass, 4x fan mounts. Value glass-panel mATX case.', 79.00, 16),
(13, 12, 'Corsair 4000D Airflow', 'CSE-CORS-4000DAIR', 'ATX, high-airflow front, 2x 120mm fans. The default recommendation.', 94.00, 15),
(13, 12, 'Corsair iCUE Link RX120 RGB', 'CSE-CORS-ICUELINK', 'ATX, 3x 120mm iCUE Link RGB fans, 360mm radiator. Clean cable-free lighting.', 119.00, 8),
(13, 5, 'MSI MPG X670E Carbon Enigma', 'CSE-MSI-MPGX670E', 'ATX, 4x 140mm fans, tempered glass, 2x 420mm. Premium airflow showcase case.', 349.00, 4),
(14, 4, 'ASUS ROG Rapture GT-AX11000', 'NET-ASUS-ROGRAPTU', 'Tri-band Wi-Fi 6E, 10GbE, 2.5GbE, 6GHz. Enthusiast gaming router.', 649.00, 4),
(14, 4, 'ASUS RT-AX57 Go', 'NET-ASUS-RT-AX57G', 'Wi-Fi 6, 2.5GbE, USB. Compact travel and small-office router.', 169.00, 12),
(14, 4, 'ASUS TUF Gaming AX6000', 'NET-ASUS-TUFGAMIN', 'Wi-Fi 6, 2.5GbE, 4 antennas, 6GHz-ready. Durable gaming router.', 179.00, 9),
(14, 5, 'MSI MEGA G1S Wi-Fi 7 Router', 'NET-MSI-MEGAG1S', 'Tri-band Wi-Fi 7, 10GbE, 6GHz. One of the first Wi-Fi 7 desktop routers.', 599.00, 3),
(14, 5, 'MSI AX3000 Wi-Fi 6 Router', 'NET-MSI-AX3000WI', 'Dual-band Wi-Fi 6, 2.5GbE, MU-MIMO. Budget 2.5GbE router.', 79.00, 17),
(14, 7, 'Dell EMC PowerConnect N2232 Switch', 'NET-DELL-EMCPOWER', '8x 2.5GbE + 2x 10GbE managed switch. Compact SMB core switch.', 229.00, 6),
(14, 8, 'HP 1920-24G-PoE+ Switch', 'NET-HP-1920-24G', '24-port gigabit PoE+ managed switch. Power and data in one cable.', 199.00, 7),
(14, 15, 'WD My Cloud Home 8TB NAS', 'NET-WEST-MYCLOUD', '8TB private cloud, 1GbE. Cheap NAS for backups and family media.', 219.00, 10),
(15, 13, 'Logitech MX Master 3S Charging Pad', 'ACC-LOGI-MXMASTER', 'Qi wireless charging for MX mice, USB-C. Cable-free desk accessory.', 99.00, 9),
(15, 12, 'Corsair iCUE Link System Hub', 'ACC-CORS-ICUELINK', 'Hubs fans and RGB in one cable. Cuts cable count in an RGB build.', 129.00, 7),
(15, 11, 'Kingston HyperX Cloud II Wireless', 'ACC-KING-HYPERXCL', '7.1 surround, 30h battery, detachable mic. Comfortable wireless gaming headset.', 149.00, 8),
(15, 15, 'WD My Passport 2TB Portable', 'ACC-WEST-MYPASSPO', '2TB, 1000 MB/s USB 3.2 Gen2. Rugged bus-powered backup drive.', 119.00, 13),
(15, 10, 'Samsung T9 2TB Portable SSD', 'ACC-SAMS-T92TB', '2000 MB/s, USB 3.2 Gen2x2, IP65 pocket drive. Fast rugged portable SSD.', 149.00, 11),
(15, 12, 'Corsair RM PX Modular Cable Kit', 'ACC-CORS-RMPX', 'Full replacement modular cables for RM series PSUs. Useful when a cable fails.', 29.00, 18),
(15, 13, 'Logitech Litra Glow Light Bar', 'ACC-LOGI-LITRAGLO', 'Adjustable desktop light bar for video calls and streaming desks.', 69.00, 15),
(15, 4, 'ASUS TUF Cable Management Kit', 'ACC-ASUS-TUFCABLE', 'Velcro ties, comb and adhesive clips. Tidies any custom build interior.', 19.00, 22),
(3, 2, 'AMD Ryzen 7 9800X3D', 'CPU-AMD-R7-9800X3D', '8C/16T Zen 5 with 3D V-Cache. The fastest single-socket gaming CPU available.', 479.00, 6),
(3, 1, 'Intel Core Ultra 7 265K', 'CPU-INTEL-U7-265K', '20 cores (8P+12E) with NPU, up to 5.5GHz. Arrow Lake hybrid for mixed workloads.', 389.00, 12);
-- Recompute product status from stock (the INSERTs above run with the
-- schema default OUT_OF_STOCK for every row, so a fresh install would
-- otherwise mark everything unavailable).
-- ------------------------------------------------------------
UPDATE products SET status = 'OUT_OF_STOCK' WHERE stock_quantity = 0 AND status = 'OUT_OF_STOCK';
UPDATE products SET status = 'LOW_STOCK'     WHERE stock_quantity BETWEEN 1 AND 5;
UPDATE products SET status = 'IN_STOCK'      WHERE stock_quantity > 5;
