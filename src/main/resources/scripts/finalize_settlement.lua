-- KEYS[1] = auctionKey
-- KEYS[2] = winnerWalletKey
-- KEYS[3] = sellerWalletKey
-- ARGV[1] = auctionId
-- ARGV[2] = winnerId
-- ARGV[3] = escrowAmount
-- ARGV[4] = sellerId
-- ARGV[5] = channelWalletUpdates ("wallet:updates")

-- 1. Idempotency Check: If the hash is gone, we already processed this. Stop immediately.
if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

-- 2. Delete Auction Hash
redis.call('DEL', KEYS[1])

-- 3. Wallet Updates & Pub/Sub (Only if there was a winner)
if ARGV[2] and ARGV[2] ~= "" and tonumber(ARGV[3]) > 0 then

    -- Decrement Winner Locked
    redis.call('HINCRBY', KEYS[2], 'locked', -tonumber(ARGV[3]))

    -- Increment Seller Available Balance
    redis.call('HINCRBY', KEYS[3], 'balance', tonumber(ARGV[3]))

    local channelWalletUpdates = ARGV[5]

    -- Publish Winner Update
    local wAvail = redis.call('HGET', KEYS[2], 'balance') or "0"
    local wLocked = redis.call('HGET', KEYS[2], 'locked') or "0"
    local winnerPayload = cjson.encode({userId = tonumber(ARGV[2]), balance = wAvail, locked = wLocked})
    redis.call('PUBLISH', channelWalletUpdates, winnerPayload)

    -- Publish Seller Update
    local sAvail = redis.call('HGET', KEYS[3], 'balance') or "0"
    local sLocked = redis.call('HGET', KEYS[3], 'locked') or "0"
    local sellerPayload = cjson.encode({userId = tonumber(ARGV[4]), balance = sAvail, locked = sLocked})
    redis.call('PUBLISH', channelWalletUpdates, sellerPayload)
end

return 1