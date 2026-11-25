package com.hackathon.kintai.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "t_user")
@Data
public class User {

    @Id
    @Column(name = "user_id", length = 10, nullable = false)
    private String userId;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "name", length = 20, nullable = false)
    private String name;

    @Column(name = "role", length = 10, nullable = false)
    private String role; // PARTNER / ADMIN

    @Column(name = "department", length = 50, nullable = false)
    private String department;

    @Column(name = "telephone_num", length = 15)
    private String telephoneNum;

    @Column(name = "mail_address", length = 100)
    private String mailAddress;

    @Column(name = "work_place", length = 100)
    private String workPlace;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.department == null || this.department.isBlank()) {
            this.department = "未設定";
        }
    }
}