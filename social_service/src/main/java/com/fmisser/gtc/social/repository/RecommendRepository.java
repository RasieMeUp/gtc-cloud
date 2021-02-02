package com.fmisser.gtc.social.repository;

import com.fmisser.gtc.base.dto.social.RecommendAnchorDto;
import com.fmisser.gtc.base.dto.social.RecommendDto;
import com.fmisser.gtc.social.domain.Recommend;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RecommendRepository extends JpaRepository<Recommend, Long> {

    @Query(value = "SELECT tr.type AS type, tr.level AS level, " +
            "tu.digit_id AS digitId, tu.nick AS nick, tu.phone AS phone, tu.gender AS gender " +
            "FROM t_recommend tr " +
            "INNER JOIN t_user tu ON tu.id = tr.user_id AND " +
            "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) " +
            "WHERE tr.recommend = 1 AND tr.type = ?3 " +
            "ORDER BY tr.level ",
            countQuery = "SELECT COUNT(tr.id) " +
                    "FROM t_recommend tr " +
                    "INNER JOIN t_user tu ON tu.id = tr.user_id AND " +
                    "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
                    "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) " +
                    "WHERE tr.recommend = 1 AND tr.type = ?3 ",
            nativeQuery = true)
    Page<RecommendDto> getRecommendList(String digitId, String nick, Integer type, Pageable pageable);

    Optional<Recommend> findByUserIdAndType(Long userId, int type);

    List<Recommend> findByType(int type);

    // 获取随机推荐主播
    // jpa sql 无法使用 rand 函数，这里取出所有数据再筛选
    @Query(value = "SELECT new com.fmisser.gtc.base.dto.social.RecommendAnchorDto(" +
            "tu.id, tu.digitId, tu.nick, tu.gender, tu.head) " +
            "FROM Recommend tr " +
            "INNER JOIN User tu ON tu.id = tr.userId " +
            "WHERE tr.recommend = 1 AND tr.type = 3 " +
//            "ORDER BY RAND() LIMIT ?1 ")
            " ORDER BY tr.id")
    List<RecommendAnchorDto> getRandRecommendAnchor();
}
