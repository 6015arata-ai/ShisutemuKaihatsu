package com.hackathon.kintai.controller;

import com.hackathon.kintai.model.User;
import com.hackathon.kintai.repository.UserRepository;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

@WebListener
@Component
public class SessionListener implements HttpSessionListener {

    @Autowired
    private UserRepository userRepo;

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        System.out.println("【セッション開始】新しいセッションが作成されました。ID: " + se.getSession().getId());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        if (userRepo == null) {
            try {
                WebApplicationContextUtils
                    .getRequiredWebApplicationContext(se.getSession().getServletContext())
                    .getAutowireCapableBeanFactory()
                    .autowireBean(this);
            } catch (Exception e) {
                // アプリ終了時などコンテキストが閉じている場合は無視
            }
        }

        User user = (User) se.getSession().getAttribute("user");

        if (user != null && user.getUserId() != null) {
            try {
                User dbUser = userRepo.findById(user.getUserId()).orElse(null);
                if (dbUser != null) {
                    System.out.println("【セッション終了】ユーザーID: " + dbUser.getUserId());
                }
            } catch (Exception e) {
                System.out.println("セッション終了処理中にエラーが発生しました: " + e.getMessage());
            }
        }
    }
}