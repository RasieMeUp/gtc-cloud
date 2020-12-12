package com.fmisser.gtc.social.repository;

import com.fmisser.gtc.base.dto.social.DynamicCommentDto;
import com.fmisser.gtc.social.domain.DynamicComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DynamicCommentRepository extends JpaRepository<DynamicComment, Long> {
    Page<DynamicComment> findByDynamicIdAndIsDeleteOrderByCreateTimeDesc(Long dynamicId, int isDelete, Pageable pageable);


    @Query(value = "SELECT new com.fmisser.gtc.base.dto.social.DynamicCommentDto" +
            "(tdc.id, tdc.dynamicId, tdc.commentIdTo, tu.id, tu2.id, tu.digitId, tu2.digitId, " +
            "tdc.content, tdc.createTime, tu.nick, tu2.nick, tu.head) " +
            "FROM DynamicComment tdc " +
            "INNER JOIN User tu ON tu.id = tdc.userIdFrom " +
            "LEFT JOIN User tu2 ON tu2.id = tdc.userIdTo " +
            "WHERE tdc.isDelete = 0 AND tdc.dynamicId = :dynamicId GROUP BY tdc.id " +
            "ORDER BY tdc.id DESC")
    Page<DynamicCommentDto> getCommentList(@Param("dynamicId") Long dynamicId, Pageable pageable);
}
