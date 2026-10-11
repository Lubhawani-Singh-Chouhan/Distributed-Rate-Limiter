--[[
  token_bucket.lua

  Atomic refill + consume for a single client's token bucket.

  Running the whole "read -> compute refill -> compare -> write" sequence as one
  Lua script is what makes this safe under concurrency: Redis executes a script
  to completion before processing any other command, so there is no window in
  which two callers can both read the same `tokens` value and both believe they
  successfully consumed the last token (the classic GET-then-SET race).

  Time comes from Redis TIME, not from the caller. App-instance clocks drift;
  using a caller-supplied `now` would let a fast clock refill extra tokens and a
  slow clock stall refill. Redis TIME is one clock for every instance.

  KEYS[1] - bucket_key                e.g. "ratelimit:bucket:acme-corp"
  ARGV[1] - capacity                  max tokens the bucket can hold (burst size)
  ARGV[2] - refill_rate               tokens added per second
  ARGV[3] - requested_tokens          tokens this request wants to consume (usually 1)

  Storage: a Redis Hash with fields `tokens` (float) and `last_refill` (ms).

  Returns a 3-element array:
    [1] allowed         1 if the request may proceed, 0 otherwise
    [2] remaining_tokens tokens left in the bucket *after* this decision (floored)
    [3] retry_after_ms   estimated ms until enough tokens exist (0 when allowed)
]]

local bucket_key = KEYS[1]

local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

if capacity == nil or refill_rate == nil or requested == nil then
  return redis.error_reply("token_bucket.lua: capacity, refill_rate, and requested_tokens must be numeric")
end

-- TIME returns {seconds, microseconds}. Convert to epoch millis.
local redis_time = redis.call("TIME")
local now = tonumber(redis_time[1]) * 1000 + math.floor(tonumber(redis_time[2]) / 1000)

local bucket = redis.call("HMGET", bucket_key, "tokens", "last_refill")
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- First time we see this client: start with a full bucket, Redis TIME as the refill anchor.
if tokens == nil or last_refill == nil then
  tokens = capacity
  last_refill = now
end

-- Negative elapsed can only happen if a leftover last_refill was written by an
-- older caller-supplied clock. Clamp; do not refill from a future stamp.
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
    retry_after_ms = -1
  end
end

redis.call("HSET", bucket_key, "tokens", tostring(new_tokens), "last_refill", tostring(now))

-- Idle buckets expire after 2x time-to-full-refill (margin so a slow client is
-- not evicted the instant the bucket would have refilled). PEXPIRE is ms-precise.
local ttl_ms
if refill_rate > 0 then
  ttl_ms = math.ceil((capacity / refill_rate) * 2000.0)
  if ttl_ms < 1000 then
    ttl_ms = 1000
  end
else
  ttl_ms = 3600000
end
redis.call("PEXPIRE", bucket_key, ttl_ms)

return {allowed, math.floor(new_tokens), retry_after_ms}
