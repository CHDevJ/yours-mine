package com.My.market.repository;

import com.My.market.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByUsername(String username);

    @Query(value = "SELECT id FROM user WHERE username = :uid", nativeQuery = true)
    Long findByUid(@Param("uid") String Uid);
}
