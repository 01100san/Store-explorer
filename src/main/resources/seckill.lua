-- 1. 参数列表
-- 1.1 优惠券id
local voucherId = ARGV[1]
-- 1.2 用户id
local userId = ARGV[2]

-- 2. 数据key     lua 中的 .. 是拼接的意思
-- 2.1 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2 订单key
local orderKey = 'seckill:order:' .. userId

-- 3. 脚本业务
-- 3.1 判断当前库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足返回 1
    return 1
end

-- 3.2 判断当前用户是否下过单  SISMEMBER k1 u1
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 存在说明是用户重复下单，返回2
    return 2
end

-- 3.3 扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.4 下单 sadd orderKey userId
redis.call('sadd', orderKey, userId)

return 0