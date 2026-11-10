package com.My.market.repository;

import com.My.market.model.Board;
import com.My.market.model.LikeState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LikeStateRepository extends JpaRepository<LikeState, Long> {

    @Query(value = "SELECT * FROM like_state ls WHERE ls.user_id = :u_id AND ls.board_id = :id", nativeQuery = true)
    LikeState findByUIdAndBId(@Param("u_id") Long u_id, @Param("id") Long id);

    @Query(value = "SELECT * FROM like_state ls WHERE ls.user_id = :u_id AND ls.board_id = :id", nativeQuery = true)
    Optional<LikeState> findByUIdBId(@Param("u_id") Long u_id, @Param("id") Long id);
}
