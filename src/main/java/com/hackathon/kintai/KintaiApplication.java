package com.hackathon.kintai;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.hackathon.kintai.model.User;
import com.hackathon.kintai.repository.UserRepository;

@SpringBootApplication
public class KintaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KintaiApplication.class, args);
    }

    @Bean
    CommandLineRunner init(UserRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                User admin = new User();
                admin.setUserId("admin");
                admin.setPasswordHash("admin123"); // ※後ほどBCrypt等でハッシュ化に切り替え
                admin.setName("管理者");
                admin.setRole("ADMIN");
                admin.setDepartment("管理部");
                admin.setIsActive(true);
                repo.save(admin);
                System.out.println("★初期管理者を自動登録しました (ID: admin)");
            }
        };
    }
}