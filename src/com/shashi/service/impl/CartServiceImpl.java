package com.shashi.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.shashi.beans.CartBean;
import com.shashi.beans.ProductBean;
import com.shashi.service.CartService;
import com.shashi.utility.DBUtil;
import com.shashi.utility.RedisUtil;
import redis.clients.jedis.Jedis;

public class CartServiceImpl implements CartService {

	@Override
	public String addProductToCart(String userId, String prodId, int prodQty) {
		String status = "Failed to Add into Cart";

		if (userId == null) {
			userId = UUID.randomUUID().toString();
			System.out.println(userId);

			RedisUtil redisUtil = new RedisUtil();
			Jedis jedis = redisUtil.getJedis();

			String cartKey = "cart:" + userId;
			String cartItem = jedis.hget(cartKey, prodId);

			if (cartItem != null) {
				int existingQty = Integer.parseInt(cartItem);
				prodQty += existingQty;
			}

			jedis.hset(cartKey, prodId, String.valueOf(prodQty));
			status = "Product added to temporary cart!";

			redisUtil.close();
		} else {
			Connection con = DBUtil.provideConnection();
			PreparedStatement ps = null;
			ResultSet rs = null;

			try {
				ps = con.prepareStatement("SELECT * FROM usercart WHERE username=? AND prodid=?");
				ps.setString(1, userId);
				ps.setString(2, prodId);

				rs = ps.executeQuery();

				if (rs.next()) {
					int cartQuantity = rs.getInt("quantity");
					ProductBean product = new ProductServiceImpl().getProductDetails(prodId);
					int availableQty = product.getProdQuantity();
					prodQty += cartQuantity;

					if (availableQty < prodQty) {
						status = updateProductToCart(userId, prodId, availableQty);
						status = "Only " + availableQty + " of " + product.getProdName() + " are available in stock!";
					} else {
						status = updateProductToCart(userId, prodId, prodQty);
					}
				}
			} catch (SQLException e) {
				status = "Error: " + e.getMessage();
				e.printStackTrace();
			} finally {
				DBUtil.closeConnection(con);
				DBUtil.closeConnection(ps);
				DBUtil.closeConnection(rs);
			}
		}

		return status;
	}

	@Override
	public String updateProductToCart(String userId, String prodId, int prodQty) {
		return "";
	}

	@Override
	public List<CartBean> getAllCartItems(String userId) {
		List<CartBean> items = new ArrayList<>();

		if (userId == null) {
			RedisUtil redisUtil = new RedisUtil();
			Jedis jedis = redisUtil.getJedis();

			String cartKey = "cart:" + userId;
			Map<String, String> cartItems = jedis.hgetAll(cartKey);

			for (Map.Entry<String, String> entry : cartItems.entrySet()) {
				CartBean cart = new CartBean();
				cart.setProdId(entry.getKey());
				cart.setQuantity(Integer.parseInt(entry.getValue()));
				items.add(cart);
			}

			redisUtil.close();
		} else {
			Connection con = DBUtil.provideConnection();
			PreparedStatement ps = null;
			ResultSet rs = null;
			try {
				ps = con.prepareStatement("SELECT * FROM usercart WHERE username=?");
				ps.setString(1, userId);
				rs = ps.executeQuery();

				while (rs.next()) {
					CartBean cart = new CartBean();
					cart.setUserId(rs.getString("username"));
					cart.setProdId(rs.getString("prodid"));
					cart.setQuantity(rs.getInt("quantity"));
					items.add(cart);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				DBUtil.closeConnection(con);
				DBUtil.closeConnection(ps);
				DBUtil.closeConnection(rs);
			}
		}
		return items;
	}

	@Override
	public int getCartCount(String userId) {
		int count = 0;

		if (userId == null) {
			// Người dùng chưa đăng nhập
			RedisUtil redisUtil = new RedisUtil();
			Jedis jedis = redisUtil.getJedis();

			String cartKey = "cart:" + userId;
			Map<String, String> cartItems = jedis.hgetAll(cartKey);

			for (String quantity : cartItems.values()) {
				count += Integer.parseInt(quantity);
			}

			redisUtil.close();
		} else {
			// Người dùng đã đăng nhập
			Connection con = DBUtil.provideConnection();
			PreparedStatement ps = null;
			ResultSet rs = null;

			try {
				ps = con.prepareStatement("SELECT SUM(quantity) FROM usercart WHERE username=?");
				ps.setString(1, userId);
				rs = ps.executeQuery();

				if (rs.next()) {
					count = rs.getInt(1);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				DBUtil.closeConnection(con);
				DBUtil.closeConnection(ps);
				DBUtil.closeConnection(rs);
			}
		}
		return count;
	}

	@Override
	public String removeProductFromCart(String userId, String prodId) {
		String status = "Product Removal Failed";

		if (userId == null) {
			RedisUtil redisUtil = new RedisUtil();
			Jedis jedis = redisUtil.getJedis();

			String cartKey = "cart:" + userId;

			jedis.hdel(cartKey, prodId);
			status = "Product successfully removed from temporary cart!";

			redisUtil.close();
		} else {
			Connection con = DBUtil.provideConnection();
			PreparedStatement ps = null;
			ResultSet rs = null;

			try {
				ps = con.prepareStatement("select * from usercart where username=? and prodid=?");
				ps.setString(1, userId);
				ps.setString(2, prodId);
				rs = ps.executeQuery();

				if (rs.next()) {
					ps = con.prepareStatement("delete from usercart where username=? and prodid=?");
					ps.setString(1, userId);
					ps.setString(2, prodId);

					int k = ps.executeUpdate();
					if (k > 0)
						status = "Product successfully removed from cart!";
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				DBUtil.closeConnection(con);
				DBUtil.closeConnection(ps);
				DBUtil.closeConnection(rs);
			}
		}
		return status;
	}

	@Override
	public boolean removeAProduct(String userId, String prodId) {
		boolean flag = false;

		Connection con = DBUtil.provideConnection();

		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			ps = con.prepareStatement("delete from usercart where username=? and prodid=?");
			ps.setString(1, userId);
			ps.setString(2, prodId);

			int k = ps.executeUpdate();

			if (k > 0)
				flag = true;

		} catch (SQLException e) {
			flag = false;
			e.printStackTrace();
		}

		DBUtil.closeConnection(con);
		DBUtil.closeConnection(ps);
		DBUtil.closeConnection(rs);

		return flag;
	}

	public int getProductCount(String userId, String prodId) {
		int count = 0;

		Connection con = DBUtil.provideConnection();

		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			ps = con.prepareStatement("select sum(quantity) from usercart where username=? and prodid=?");
			ps.setString(1, userId);
			ps.setString(2, prodId);
			rs = ps.executeQuery();

			if (rs.next() && !rs.wasNull())
				count = rs.getInt(1);

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return count;
	}

	@Override
	public int getCartItemCount(String userId, String itemId) {
		int count = 0;
		if (userId == null || itemId == null)
			return 0;
		Connection con = DBUtil.provideConnection();

		PreparedStatement ps = null;

		ResultSet rs = null;

		try {
			ps = con.prepareStatement("select quantity from usercart where username=? and prodid=?");

			ps.setString(1, userId);
			ps.setString(2, itemId);

			rs = ps.executeQuery();

			if (rs.next() && !rs.wasNull())
				count = rs.getInt(1);

		} catch (SQLException e) {

			e.printStackTrace();
		}

		DBUtil.closeConnection(con);
		DBUtil.closeConnection(ps);
		DBUtil.closeConnection(rs);

		return count;
	}
}
