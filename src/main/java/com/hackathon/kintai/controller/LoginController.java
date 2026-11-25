package com.hackathon.kintai.controller;

import com.hackathon.kintai.model.User;
import com.hackathon.kintai.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class LoginController {

    @Autowired
    private UserRepository userRepo;

    @GetMapping("/")
    public String loginPage(HttpSession session, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser != null && sessionUser.getUserId() != null) {
            User dbUser = userRepo.findById(sessionUser.getUserId()).orElse(null);
            if (dbUser != null && !Boolean.FALSE.equals(dbUser.getIsActive())) {
                return "redirect:/partner";
            }
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String loginInfo, @RequestParam String password, HttpSession session) {
        User user = userRepo.findByUserId(loginInfo)
                .orElseGet(() -> userRepo.findByName(loginInfo).orElse(null));

        if (user == null || Boolean.FALSE.equals(user.getIsActive())
                || user.getPasswordHash() == null || !user.getPasswordHash().equals(password)) {
            return "redirect:/?error=invalid_password";
        }

        session.setAttribute("user", user);
        return "redirect:/partner";
    }

    @RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public String logout(HttpSession session, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        session.invalidate();
        return "redirect:/?logout";
    }

    @GetMapping("/debug/reset-login")
    @ResponseBody
    public String resetLogin(HttpSession session) {
        session.invalidate();
        return "現在のセッションをリセットしました。";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String userId,
                                @RequestParam String name,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword) {

        User user = userRepo.findByUserId(userId).orElse(null);

        if (user == null) {
            return "redirect:/forgot-password?error=user_not_found";
        }
        if (!user.getName().equals(name)) {
            return "redirect:/forgot-password?error=name_mismatch";
        }

        if (newPassword.matches(".*[^\\x21-\\x7e].*")) {
            return "redirect:/forgot-password?error=invalid_characters";
        }

        if (!newPassword.equals(confirmPassword)) {
            return "redirect:/forgot-password?error=password_mismatch";
        }
        if (newPassword.equals(user.getPasswordHash())) {
            return "redirect:/forgot-password?error=same_as_old";
        }

        user.setPasswordHash(newPassword);
        userRepo.save(user);

        return "redirect:/?reset_success=true";
    }

    @GetMapping("/change-lang")
    public String changeLanguage(@RequestParam("locale") String locale,
                                 @RequestParam(value = "redirect", defaultValue = "/") String redirect) {
        return "redirect:" + redirect;
    }
}