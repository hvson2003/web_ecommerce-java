package com.shashi.utility;

import redis.clients.jedis.Jedis;
import java.util.HashMap;
import java.util.Map;

public class RedisUtil {

    private Jedis jedis;

    public RedisUtil() {
        this.jedis = new Jedis("localhost", 6379);
    }

    public Jedis getJedis() {
        return jedis;
    }

    public Map<String, Integer> getCart(String cartKey) {
        Map<String, String> redisCart = jedis.hgetAll(cartKey);
        Map<String, Integer> cart = new HashMap<>();

        for (Map.Entry<String, String> entry : redisCart.entrySet()) {
            try {
                cart.put(entry.getKey(), Integer.parseInt(entry.getValue())); // Chuyển String sang Integer
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        return cart;
    }

    public void saveCart(String cartKey, Map<String, Integer> cartItems) {
        Map<String, String> redisCart = new HashMap<>();
        for (Map.Entry<String, Integer> entry : cartItems.entrySet()) {
            redisCart.put(entry.getKey(), entry.getValue().toString()); // Chuyển Integer sang String trước khi lưu vào Redis
        }
        jedis.hmset(cartKey, redisCart); // Lưu giỏ hàng vào Redis dưới dạng Map<String, String>
    }

    public void deleteCart(String cartKey) {
        jedis.del(cartKey);
    }

    public void close() {
        jedis.close();
    }
}
