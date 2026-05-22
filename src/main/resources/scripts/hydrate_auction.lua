-- KEYS[1] = "auction:{id}"
-- KEYS[2] = "auctions:upcoming"
-- ARGV[1] = auctionId
-- ARGV[2] = startTimeEpoch
-- ARGV[3], ARGV[4], ARGV[5], ARGV[6] ... = field1, value1, field2, value2 ...

-- 1. If it already exists, do nothing and return 0
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

-- 2. Extract all the hash fields and values from the dynamic arguments
local hashArgs = {}
for i = 3, #ARGV do
    table.insert(hashArgs, ARGV[i])
end

-- 3. Save the Hash and update the Upcoming index
redis.call('HSET', KEYS[1], unpack(hashArgs))
redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])

return 1