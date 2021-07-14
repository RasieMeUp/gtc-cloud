package com.fmisser.gtc.social.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.util.Date;

/**
 * @author by fmisser
 * @create 2021/7/7 5:00 下午
 * @description TODO
 */

@Entity
@Table(name = "t_friend",
        indexes = {@Index(columnList = "userIdFrom,userIdTo,status,createTime"),
@Index(columnList = "userIdTo,status,createTime")})
@Data
@EntityListeners(AuditingEntityListener.class)
public class Friend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //
    @Column(nullable = false)
    private Long userIdFrom;

    //
    @Column(nullable = false)
    private Long userIdTo;

    @CreatedDate
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column
    private Date createTime;

    @LastModifiedDate
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column
    private Date modifyTime;

    /**
     * 0 是否是好友
     */
    @Column(nullable = false, columnDefinition = "int default 1")
    private int status = 1;
}
