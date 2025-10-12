-- Add winner_id column to track auction winners
--
-- This column is populated when:
-- 1. An auction ends (status becomes ENDED)
-- 2. Item Service consumes BidPlacedEvent and updates the current highest bidder
--
-- Nullable: winnerId is NULL until auction ends or for auctions with no bids
ALTER TABLE items
ADD COLUMN winner_id UUID;

COMMENT ON COLUMN items.winner_id IS 'UUID of winning bidder (current highest). NULL until first bid placed.';