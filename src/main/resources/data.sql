INSERT INTO auctions (id, title, auction_type, status, start_price, winning_bid, product_id, seller_id, winner_id,
                      start_time, end_time)
VALUES
-- 1. Original duration: 6 hours. Ends at May 19, 3:40 AM IST (May 18, 22:10 UTC)
(1001, 'RTX 5090 Midnight Drop', 'ANTI_SNIPE', 'ACTIVE', 150000, 150000, 201, 1, NULL,
 '2026-05-18T16:10:00Z', '2026-05-18T22:10:00Z'),

-- 2. Original duration: 3 hours. Ends at May 19, 12:40 AM IST (May 18, 19:10 UTC)
(1002, 'Travis Scott Fragment AJ1', 'STANDARD', 'ACTIVE', 120000, 145000, 203, 2, NULL,
 '2026-05-18T16:10:00Z', '2026-05-18T19:10:00Z'),

-- 3. Original duration: 8 hours. Ends at May 19, 5:40 AM IST (May 18, 23:40 UTC)
(1003, 'MacBook Pro M5 Max Overkill', 'STANDARD', 'UPCOMING', 350000, NULL, 202, 1, NULL,
 '2026-05-18T16:10:00Z', '2026-05-18T23:40:00Z'),

-- 4. Original duration: 12 hours. Ends at May 19, 9:40 AM IST (May 19, 04:10 UTC)
(1004, 'Rolex Submariner Gold Edition', 'ANTI_SNIPE', 'UPCOMING', 950000, NULL, 204, 3, NULL,
 '2026-05-18T16:10:00Z', '2026-05-19T04:10:00Z'),

-- 5. Original duration: 24 hours. Ends at May 19, 9:40 PM IST (May 19, 16:10 UTC)
(1005, 'Sony PS6 Developer Kit', 'ANTI_SNIPE', 'UPCOMING', 500000, NULL, 205, 1, NULL,
 '2026-05-18T16:10:00Z', '2026-05-19T16:10:00Z')
ON CONFLICT (id) DO NOTHING;