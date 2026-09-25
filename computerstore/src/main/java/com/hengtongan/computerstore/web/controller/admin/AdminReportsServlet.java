package com.hengtongan.computerstore.web.controller.admin;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/admin/reports")
public class AdminReportsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("summary", app().reportService().getSalesSummary());
        request.setAttribute("stockValues", app().reportService().getLowStockValues());
        request.setAttribute("lowStock", app().reportService().getLowStock());
        request.setAttribute("outOfStock", app().reportService().getOutOfStock());
        request.getRequestDispatcher("/WEB-INF/views/admin/reports.jsp").forward(request, response);
    }
}