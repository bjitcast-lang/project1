-- 令牌桶限流
-- KEYS[1] = shortlink:limit:create:{ip}
-- ARGV[1] = capacity  ARGV[2] = rate(个/秒)  ARGV[3] = now(ms)  ARGV[4] = ttl(s)
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts = tonumber(data[2])
if tokens == nil then
    tokens = capacity
    ts = now
end

local delta = math.max(0, now - ts)
tokens = math.min(capacity, tokens + delta * rate / 1000)

local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
redis.call('EXPIRE', key, ttl)
return allowed
