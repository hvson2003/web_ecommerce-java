package com.shashi.srv;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.shashi.beans.ProductBean;
import com.shashi.service.impl.CartServiceImpl;
import com.shashi.service.impl.ProductServiceImpl;
import com.shashi.service.impl.DemandServiceImpl;
import com.shashi.beans.DemandBean;
import com.shashi.utility.RedisUtil;

@WebServlet("/AddtoCart")
public class AddtoCart extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public AddtoCart() {
		super();
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		HttpSession session = request.getSession();
		String userName = (String) session.getAttribute("username");
		String password = (String) session.getAttribute("password");
		String usertype = (String) session.getAttribute("usertype");

		PrintWriter pw = response.getWriter();
		response.setContentType("text/html");

		if (userName == null || password == null || usertype == null || !usertype.equalsIgnoreCase("customer")) {
			userName = "user_" + UUID.randomUUID().toString().substring(0, 8);

			String cartKey = "cart:" + userName;
			RedisUtil redisUtil = new RedisUtil();
			Map<String, Integer> cartItems = redisUtil.getCart(cartKey);
			if (cartItems == null) {
				cartItems = new HashMap<>();
			}

			String prodId = request.getParameter("pid");
			int pQty = Integer.parseInt(request.getParameter("pqty"));
			synchronized (redisUtil) {
				cartItems.put(prodId, cartItems.getOrDefault(prodId, 0) + pQty);
				redisUtil.saveCart(cartKey, cartItems);
			}
			redisUtil.close();

			pw.println("<script>document.getElementById('message').innerHTML='Product added to your temporary cart!'</script>");
			RequestDispatcher rd = request.getRequestDispatcher("userHome.jsp");
			rd.include(request, response);
			return;
		}

		String userId = userName;
		String prodId = request.getParameter("pid");
		int pQty = Integer.parseInt(request.getParameter("pqty"));

		CartServiceImpl cart = new CartServiceImpl();
		ProductServiceImpl productDao = new ProductServiceImpl();

		ProductBean product = productDao.getProductDetails(prodId);
		int availableQty = product.getProdQuantity();

		int cartQty = cart.getProductCount(userId, prodId);
		pQty += cartQty;

		if (pQty == cartQty) {
			String status = cart.removeProductFromCart(userId, prodId);
			RequestDispatcher rd = request.getRequestDispatcher("userHome.jsp");
			rd.include(request, response);
			pw.println("<script>document.getElementById('message').innerHTML='" + status + "'</script>");
		} else if (availableQty < pQty) {
			String status = null;
			if (availableQty == 0) {
				status = "Product is Out of Stock!";
			} else {
				cart.updateProductToCart(userId, prodId, availableQty);
				status = "Only " + availableQty + " of " + product.getProdName() + " are available in the shop!";
			}

			DemandBean demandBean = new DemandBean(userName, product.getProdId(), pQty - availableQty);
			DemandServiceImpl demand = new DemandServiceImpl();
			boolean flag = demand.addProduct(demandBean);

			if (flag)
				status += "<br/>We will notify you when " + product.getProdName() + " is available in the store.";

			RequestDispatcher rd = request.getRequestDispatcher("cartDetails.jsp");
			rd.include(request, response);
			pw.println("<script>document.getElementById('message').innerHTML='" + status + "'</script>");
		} else {
			String status = cart.updateProductToCart(userId, prodId, pQty);
			RequestDispatcher rd = request.getRequestDispatcher("userHome.jsp");
			rd.include(request, response);
			pw.println("<script>document.getElementById('message').innerHTML='" + status + "'</script>");
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
}
