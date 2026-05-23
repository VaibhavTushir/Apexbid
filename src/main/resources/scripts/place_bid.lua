-- Dynamic minimum increments based on current price
--The money is stored in Cents everywhere
local function getMinIncrement(currentPrice)
    if currentPrice < 5000 then return 100           -- < $50, increment $1
    elseif currentPrice < 10000 then return 500      -- < $100, increment $5
    elseif currentPrice < 50000 then return 1000     -- < $500, increment $10
    elseif currentPrice < 100000 then return 2500    -- < $1000, increment $25
    elseif currentPrice < 500000 then return 5000    -- < $5000, increment $50
    elseif currentPrice < 1000000 then return 10000  -- < $10000, increment $100
    else return 25000 end                            -- >= $10000, increment $250
end

-- Fixed Escrow Lock Tiers
local function getEscrowLock(bidAmount)
    if bidAmount < 5000 then return 100              -- < $50, lock $1
    elseif bidAmount < 10000 then return 1000        -- < $100, lock $10
    elseif bidAmount < 50000 then return 2500        -- < $500, lock $25
    elseif bidAmount < 100000 then return 5000       -- < $1000, lock $50
    elseif bidAmount < 500000 then return 10000      -- < $5000, lock $100
    elseif bidAmount < 1000000 then return 25000     -- < $10000, lock $250
    else return 50000 end                            -- >= $10000, lock $500
end


-- KEYS & ARGS
local auctionKey = KEYS[1]
local newWalletKey = KEYS[2]
local highestBidsKey = KEYS[3]
local mostActiveKey = KEYS[4]
local activeAuctionsKey = KEYS[5]

local auctionId = ARGV[1]
local newUserId = ARGV[2]
local newUsername = ARGV[3]
local bidAmount = tonumber(ARGV[4])
local currentTime = tonumber(ARGV[5])


-- Auction Validation
local auctionData = redis.call('HMGET', auctionKey, 'status', 'end_time', 'winning_bid', 'winner_id', 'auction_type', 'seller_id', 'start_price')
local status = auctionData[1]

if status ~= "ACTIVE" then return -1 end

local endTime = tonumber(auctionData[2])
local currentBidStr = auctionData[3]
local prevWinnerId = auctionData[4]
local auctionType = auctionData[5]
local sellerId = auctionData[6]
local startPrice = tonumber(auctionData[7]) or 0

-- If no bids yet, currentBidStr will be nil, so initialize to startPrice
local currentBid = startPrice
local requiredMinimumBid = startPrice

 -- If a winning bid already exists, calculate and add the increment
if currentBidStr and currentBidStr ~= "" then
     currentBid = math.floor(tonumber(currentBidStr))
     requiredMinimumBid = currentBid + getMinIncrement(currentBid)
end
if currentTime >= endTime then return -2 end
if bidAmount < requiredMinimumBid then return -3 end
if newUserId == sellerId then return -6 end


-- 2. Wallet & Escrow Validation
if redis.call('EXISTS', newWalletKey) == 0 then return -4 end

local newWallet = redis.call('HMGET', newWalletKey, 'balance', 'locked')
local available = math.floor(tonumber(newWallet[1]) or 0)

local requiredEscrow = getEscrowLock(bidAmount)
local prevEscrow = getEscrowLock(currentBid)

local requiredFunds = requiredEscrow

--same winner is outbidding themselves
if prevWinnerId == newUserId then
    requiredFunds = requiredEscrow - prevEscrow
end

if available < requiredFunds then return -5 end


-- Update Auction State
local newEndTime = endTime
local timeRemaining = endTime - currentTime
local snipeThreshold = 10000 --10 seconds

if auctionType == "ANTI_SNIPE" and timeRemaining < snipeThreshold and prevWinnerId ~= newUserId then
    newEndTime = currentTime + snipeThreshold
    redis.call('HSET', auctionKey, 'end_time', newEndTime)
    redis.call('ZADD', activeAuctionsKey, newEndTime, auctionId)
end

redis.call('HMSET', auctionKey, 'winning_bid', bidAmount, 'winner_id', newUserId, 'winner_username', newUsername)

redis.call('ZADD', highestBidsKey, bidAmount, auctionId)
redis.call('ZINCRBY', mostActiveKey, 1, auctionId)

-- Update Wallet State
if prevWinnerId == newUserId then
    if requiredFunds > 0 then
        redis.call('HINCRBY', newWalletKey, 'balance', -requiredFunds)
        redis.call('HINCRBY', newWalletKey, 'locked', requiredFunds)
    end
else
    if prevWinnerId and prevWinnerId ~= "" then
        local prevWalletKey = "wallet:" .. prevWinnerId

        redis.call('HINCRBY', prevWalletKey, 'balance', prevEscrow)
        redis.call('HINCRBY', prevWalletKey, 'locked', -prevEscrow)

        local oldAvail = redis.call('HGET', prevWalletKey, 'balance')
        local oldLocked = redis.call('HGET', prevWalletKey, 'locked')
        --publish refund updated wallet to previous bidder
        redis.call('PUBLISH', 'wallet:updates', cjson.encode({userId = prevWinnerId, balance = oldAvail, locked = oldLocked}))
    end

    redis.call('HINCRBY', newWalletKey, 'balance', -requiredEscrow)
    redis.call('HINCRBY', newWalletKey, 'locked', requiredEscrow)

end
    local newAvail = redis.call('HGET', newWalletKey, 'balance')
    local newLocked = redis.call('HGET', newWalletKey, 'locked')
    --publish update to user channel for new winner
    redis.call('PUBLISH', 'wallet:updates', cjson.encode({userId = newUserId, balance = newAvail, locked = newLocked}))


-- Publish Auction Update
local nextThreshold = bidAmount + getMinIncrement(bidAmount)
local auctionPayload = cjson.encode({
    auctionId = auctionId,
    winningBid = bidAmount,
    winnerUsername = newUsername,
    nextThreshold = nextThreshold,
    endTime = newEndTime
})
redis.call('PUBLISH', 'auction:updates', auctionPayload)

return 1