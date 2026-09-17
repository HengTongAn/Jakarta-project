package com.example.computer_store.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class AppContextListener implements ServletContextListener {

    public static final String ATTR_NAME = "appContext";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
	AppContext.init();
	sce.getServletContext().setAttribute(ATTR_NAME, AppContext.get());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
	sce.getServletContext().removeAttribute(ATTR_NAME);
    }
}
