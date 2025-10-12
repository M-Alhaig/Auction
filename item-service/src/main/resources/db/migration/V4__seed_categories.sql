-- Seed initial categories for testing and demo purposes
-- These are common auction categories that sellers can assign to their items

INSERT INTO categories (id, name) VALUES
(1, 'Electronics'),
(2, 'Fashion & Apparel'),
(3, 'Home & Garden'),
(4, 'Sports & Outdoors'),
(5, 'Collectibles & Antiques'),
(6, 'Automotive'),
(7, 'Books & Media'),
(8, 'Jewelry & Watches'),
(9, 'Art & Crafts'),
(10, 'Toys & Games');

-- Reset the sequence to start from 11 for future inserts
-- This prevents conflicts with our seeded data
ALTER SEQUENCE categories_id_seq RESTART WITH 11;
