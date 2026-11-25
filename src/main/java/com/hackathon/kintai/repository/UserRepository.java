package com.hackathon.kintai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.hackathon.kintai.model.User;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    // ユーザーIDとパスワードハッシュで検索
    Optional<User> findByUserIdAndPasswordHash(String userId, String passwordHash);

    // 名前とパスワードハッシュで検索
    Optional<User> findByNameAndPasswordHash(String name, String passwordHash);

    Optional<User> findByUserIdAndName(String userId, String name);
    Optional<User> findByUserId(String userId);
    Optional<User> findByName(String name);
    long count();
}