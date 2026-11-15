package com.My.market.repository;

import com.My.market.model.Board;
import com.My.market.model.BoardFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.util.List;
import java.util.Optional;

public interface BoardRepository  extends JpaRepository<Board, Long> {

    List<Board> findByTitle(String title);

    @Query(value = "SELECT * FROM board b WHERE b.user_id = :id", nativeQuery = true)
    List<Board> findByUserId(@Param("id") Long id);

    @Query(value = "SELECT * FROM board WHERE id = :id", nativeQuery = true)
    Board findByBoardId(@Param("id") Long id);

    @Query(value = "SELECT * FROM board b WHERE b.title like %:title%", nativeQuery = true)
    Page<Board> findByTitle(@Param("title") String title, Pageable pageable);

    @Query(value = "SELECT * FROM board b WHERE b.user_id = :id AND b.title like %:title%", nativeQuery = true)
    Page<Board> findByIdTitle(@Param("id") Long id, @Param("title") String title, Pageable pageable);

    @Query(value = "SELECT b.* FROM board b INNER JOIN like_state l ON b.id = l.board_id WHERE l.user_id = :uid AND l.state = :num", nativeQuery = true)
    Page<Board> findBoardsByUserIdAndState(@Param("uid") Long uid, @Param("num") Long num, Pageable pageable);


}
