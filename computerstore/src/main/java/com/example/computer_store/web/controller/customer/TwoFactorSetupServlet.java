package com.example.computer_store.web.controller.customer;

import com.example.computer_store.web.controller.base.BaseServlet;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.infrastructure.security.TwoFactorAuthService;
import com.example.computer_store.util.security.CSRFUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Servlet for 2FA setup and management.
 */
@WebServlet("/account/2fa")
public class TwoFactorSetupServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentUser(request);
        if (user == null) {
            redirect(request, response, "/login");
            return;
        }

        request.setAttribute("twoFactorEnabled", TwoFactorAuthService.isTwoFactorEnabled(user.getUserId()));
        request.setAttribute("csrfToken", CSRFUtil.generateToken(request.getSession()));
        forward(request, response, "customer/account/2fa-setup.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentUser(request);
        if (user == null) {
            redirect(request, response, "/login");
            return;
        }

        String action = request.getParameter("action");

        if ("generate".equals(action)) {
            // Regenerating while 2FA is already on would overwrite the live
            // secret and silently disable protection — block that path.
            if (TwoFactorAuthService.isTwoFactorEnabled(user.getUserId())) {
                flashError(request, "Disable two-factor authentication before generating a new secret.");
                redirect(request, response, "/account/2fa");
                return;
            }
            TwoFactorAuthService.TwoFactorSetupResult result =
                    TwoFactorAuthService.generateSecretKey(user);
            request.setAttribute("secret", result.getSecret());
            request.setAttribute("qrCodeUrl", result.getQrCodeUrl());
            request.setAttribute("twoFactorEnabled", false);
            request.setAttribute("csrfToken", CSRFUtil.generateToken(request.getSession()));
            forward(request, response, "customer/account/2fa-setup.jsp");

        } else if ("enable".equals(action)) {
            // Enable 2FA after verifying the pending (not-yet-enabled) secret
            String code = request.getParameter("code");
            int totpCode;
            try {
                totpCode = Integer.parseInt(code);
            } catch (NumberFormatException e) {
                flashError(request, "Invalid code format");
                redirect(request, response, "/account/2fa");
                return;
            }

            if (TwoFactorAuthService.verifyPendingCode(user.getUserId(), totpCode)) {
                try {
                    TwoFactorAuthService.enableTwoFactor(user.getUserId());
                    flashSuccess(request, "Two-factor authentication enabled successfully");
                    redirect(request, response, "/account/2fa");
                } catch (Exception e) {
                    flashError(request, "Failed to enable 2FA: " + e.getMessage());
                    redirect(request, response, "/account/2fa");
                }
            } else {
                flashError(request, "Invalid verification code");
                redirect(request, response, "/account/2fa");
            }

        } else if ("disable".equals(action)) {
            // Require possession of the active authenticator before removing
            // this account protection.
            String code = request.getParameter("code");
            int totpCode;
            try {
                totpCode = Integer.parseInt(code);
                if (!TwoFactorAuthService.verifyCode(user.getUserId(), totpCode)) {
                    flashError(request, "Invalid verification code");
                    redirect(request, response, "/account/2fa");
                    return;
                }
                TwoFactorAuthService.disableTwoFactor(user.getUserId());
                flashSuccess(request, "Two-factor authentication disabled");
                redirect(request, response, "/account/2fa");
            } catch (Exception e) {
                flashError(request, "Failed to disable 2FA: " + e.getMessage());
                redirect(request, response, "/account/2fa");
            }
        } else {
            redirect(request, response, "/account/2fa");
        }
    }
}
