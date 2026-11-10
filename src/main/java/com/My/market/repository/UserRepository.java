package com.My.market.repository;

import com.My.market.model.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
public interface UserRepository extends JpaRepository<User, Long>, QuerydslPredicateExecutor<User> ,CustomizedUserRepository{

    @EntityGraph(attributePaths = { "boards" })
    List<User> findAll();

    User findByUsername(String username);

    @Query("select u from User u where u.username like %?1%")
    List<User> findByUsernameQuery(String username); //jpql

    @Query(value = "select * from user u where u.username like %?1%", nativeQuery = true)
    List<User> findByUsernameNativeQuery(String username);//sql query

    @Query(value = "SELECT id FROM user WHERE username = :uid", nativeQuery = true)
    Long findByUid(@Param("uid") String Uid);
}
