package com.example.computer_store.core.service;

import com.example.computer_store.core.domain.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    User login(String username, String plainPassword);

    User login(String username, String plainPassword, HttpServletRequest request);

    User register(String username, String fullName, String email,
                  String plainPassword, String confirmPassword);
}
