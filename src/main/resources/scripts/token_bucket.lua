--[[
  token_bucket.lua

  Atomic refill + consume for a single client's token bucket.

  Running the whole "read -> compute refill -> compare -> write" sequence as one
  Lua script is what makes this safe under concurrency: Redis executes a script
  to completion before processing any other command, so there is no window in
  which two callers can both read the same `tokens` value and both believe they
  successfully consumed the last token (the classic GET-then-SET race).

  KEYS[1] - bucket_key                e.g. "ratelimit:bucket:acme-corp"
  ARGV[1] - capacity                  max tokens the bucket can hold (burst size)
  ARGV[2] - refill_rate               tokens added per second
  ARGV[3] - now_timestamp_ms          caller-supplied "now", in epoch millis
  ARGV[4] - requested_tokens          tokens this request wants to consume (usually 1)

  Storage: a Redis Hash with fields `tokens` (float) and `last_refill` (ms, integer-ish).

  Returns a 3-element array:
    [1] allowed         1 if the request may proceed, 0 otherwise
    [2] remaining_tokens tokens left in the bucket *after* this decision (floored)
    [3] retry_after_ms   estimated ms until enough tokens exist (0 when allowed)
]]

local bucket_key = KEYS[1]

local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

if capacity == nil or refill_rate == nil or now == nil or requested == nil then
  return redis.error_reply("token_bucket.lua: all ARGV values must be numeric")
end

local bucket = redis.call("HMGET", bucket_key, "tokens", "last_refill")
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- First time we see this client: start with a full bucket, "now" as the refill anchor.
if tokens == nil or last_refill == nil then
  tokens = capacity
  last_refill = now
end

-- Guard against clock skew across app instances producing a negative elapsed time.
local elapsed_ms = now - last_refill
if elapsed_ms < 0 then
  elapsed_ms = 0
end

local refill_amount = (elapsed_ms / 1000.0) * refill_rate
local new_tokens = math.min(capacity, tokens + refill_amount)

local allowed = 0
local retry_after_ms = 0

if new_tokens >= requested then
  new_tokens = new_tokens - requested
  allowed = 1
else
  allowed = 0
  local deficit = requested - new_tokens
  if refill_rate > 0 then
    retry_after_ms = math.ceil((deficit / refill_rate) * 1000.0)
  else
    -- A bucket that never refills and is currently short on tokens will never
    -- satisfy this request; surface a sentinel value the caller can treat as "never".
    retry_after_ms = -1
  end
end

redis.call("HSET", bucket_key, "tokens", tostring(new_tokens), "last_refill", tostring(now))

-- Idle buckets should eventually disappear rather than accumulate forever in Redis.
-- 2x the time needed to go from empty to full is a generous cleanup window.
if refill_rate > 0 then
  local ttl_seconds = math.ceil((capacity / refill_rate) * 2)
  if ttl_seconds < 1 then
    ttl_seconds = 1
  end
  redis.call("EXPIRE", bucket_key, ttl_seconds)
end

return {allowed, math.floor(new_tokens), retry_after_ms}
