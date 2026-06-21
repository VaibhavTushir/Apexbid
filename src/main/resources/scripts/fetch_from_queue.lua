-- KEYS[1] = Main Queue
-- KEYS[2] = Processing ZSET
-- ARGV[1] = Current Time (Epoch ms)
-- ARGV[2] = Visibility Timeout Score (Current Time + Offset)

local currentTime = tonumber(ARGV[1])
local newTimeout = tonumber(ARGV[2])

-- 1. SAFETY NET: Check for items left behind by crashed workers
local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', currentTime, 'LIMIT', 0, 1)

if expired and #expired > 0 then
    local recoveredItem = expired[1]
    -- Claim it by updating the score to the new timeout
    redis.call('ZADD', KEYS[2], newTimeout, recoveredItem)
    return recoveredItem
end

-- 2. NORMAL FLOW: Grab a fresh item from the main queue
local freshItem = redis.call('RPOP', KEYS[1])
if freshItem then
    redis.call('ZADD', KEYS[2], newTimeout, freshItem)
    return freshItem
end

return nil