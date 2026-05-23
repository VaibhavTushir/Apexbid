-- KEYS[1] = auction_key ("auction:123")
-- KEYS[2] = upcoming_zset ("auctions:upcoming")
-- KEYS[3] = active_zset ("auctions:active")
-- KEYS[4] = highest_bids_zset ("auctions:highest_bids")
-- KEYS[5] = most_active_zset ("auctions:most_active")
-- ARGV[1] = auction_id ("123")

-- Fetch all 3 fields
local auction_data = redis.call('HMGET', KEYS[1], 'status', 'end_time', 'start_price')

local status = auction_data[1]
local end_time = tonumber(auction_data[2])
local start_price = tonumber(auction_data[3])

-- Abort if wrong status or missing numeric data
if status ~= 'UPCOMING' or not end_time or not start_price then
    return 0
end

-- Execute all state changes atomically
redis.call('HSET', KEYS[1], 'status', 'ACTIVE')
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('ZADD', KEYS[3], end_time, ARGV[1])
redis.call('ZADD', KEYS[4], start_price, ARGV[1])
redis.call('ZADD', KEYS[5], 0, ARGV[1])

-- Broadcast the status change to the frontend
local payload = cjson.encode({
    auctionId = ARGV[1],
    status = "ACTIVE"
})
redis.call('PUBLISH', 'auction:updates', payload)

return 1