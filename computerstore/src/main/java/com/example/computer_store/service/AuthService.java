package com.example.computer_store.service;

import com.example.computer_store.model.User;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    User login(String username, String plainPassword);

    User login(String username, String plainPassword, HttpServletRequest request);

    User register(String username, String fullName, String email,
                  String plainPassword, String confirmPassword);
}
