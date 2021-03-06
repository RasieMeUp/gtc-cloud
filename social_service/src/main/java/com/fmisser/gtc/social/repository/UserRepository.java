package com.fmisser.gtc.social.repository;

import com.fmisser.gtc.base.dto.social.AnchorDto;
import com.fmisser.gtc.base.dto.social.ProfitConsumeDetail;
import com.fmisser.gtc.base.dto.social.ConsumerDto;
import com.fmisser.gtc.base.dto.social.calc.CalcConsumeDto;
import com.fmisser.gtc.social.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    long countByCreateTimeBetween(Date start, Date end);
    Optional<User> findByUsername(String username);
    Optional<User> findByDigitId(String digitId);
    Optional<User> findByNick(String nick);
    Page<User> findByIdentityOrderByCreateTimeDesc(int identity, Pageable pageable);


    @Query(value = "SELECT tu.nick AS nick  FROM  t_user tu  where nick=?1",nativeQuery = true)
    String getNick(String nick);

    // 注意 要开启事务, service 开启了的话这里不用开启
//    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE t_user set follows = follows + 1 WHERE id = ?1", nativeQuery = true)
    int addUserFollow(Long userId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE t_user set follows = follows - 1 WHERE id = ?1", nativeQuery = true)
    int subUserFollow(Long userId);

    // 获取主播列表,根据创建时间排序
    @Query(value = "SELECT * FROM t_user tu " +
            "WHERE tu.identity = 1 " +
            "AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " +
            "ORDER BY create_time DESC ",
            countQuery = "SELECT COUNT(*) FROM t_user tu " +
                    "WHERE tu.identity = 1 " +
                    "AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL)",
            nativeQuery = true)
    Page<User> getAnchorListByCreateTime(Integer gender, Pageable pageable);

    // 审核池中的主播
    @Query(value = "SELECT tu.* FROM t_recommend tr " +
            "INNER JOIN t_user tu ON tr.user_id = tu.id " +
            "AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " +
            "WHERE tr.type = 5 AND tr.recommend = 1",
            countQuery = "SELECT COUNT(*) FROM t_recommend tr " +
                    "INNER JOIN t_user tu ON tr.user_id = tu.id " +
                    "AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " +
                    "WHERE tr.type = 5 AND tr.recommend = 1",
            nativeQuery = true)
    Page<User> getAuditAnchorList(Integer gender, Pageable pageable);

    // 获取主播列表，根据总收益排序
    @Query(value = "SELECT tu.*, " +
            "IFNULL(SUM(tmb.profit_coin), 0) + " +
            "IFNULL(SUM(tgb.profit_coin), 0) + " +
            "IFNULL(SUM(tcb.profit_coin), 0) AS profit " +
            "FROM t_user tu " +
            "LEFT JOIN t_message_bill tmb ON tu.id = tmb.user_id_to " +
            "LEFT JOIN t_gift_bill tgb ON tu.id = tgb.user_id_to " +
            "LEFT JOIN t_call_bill tcb ON tu.id = tcb.user_id_to " +
            "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " +
            "GROUP BY tu.id ORDER BY profit DESC ",
    countQuery = "SELECT COUNT(*) FROM t_user tu " +
            "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " ,
    nativeQuery = true)
    Page<User> getAnchorListByProfit(Integer gender, Pageable pageable);

    // 获取主播列表，根据总收益排序
    @Query(value = "SELECT tu.*, " +
            "(SELECT IFNULL(SUM(tmb.profit_coin), 0) FROM t_message_bill tmb WHERE tmb.user_id_to = tu.id) + " +
            "(SELECT IFNULL(SUM(tgb.profit_coin), 0) FROM t_gift_bill tgb WHERE tgb.user_id_to = tu.id) + " +
            "(SELECT IFNULL(SUM(tcb.profit_coin), 0) FROM t_call_bill tcb WHERE tcb.user_id_to = tu.id) " +
            "AS profit " +
            "FROM t_user tu " +
            "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " +
            "GROUP BY tu.id ORDER BY profit DESC ",
            countQuery = "SELECT COUNT(*) FROM t_user tu " +
                    "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " ,
            nativeQuery = true)
    Page<User> getAnchorListByProfitEx(Integer gender, Pageable pageable);

    // 获取主播列表，根据活跃排序
    @Query(value = "SELECT tu.* FROM t_user tu " +
            "INNER JOIN " +
            "(SELECT MAX(tu.id) AS id, MAX(ta.active_time) AS activeTime " +
            "FROM t_user tu " +
            "INNER JOIN t_active ta ON ta.user_id = tu.id AND ta.status = 41 AND active_time > ?2 " +
            "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " +
            "GROUP BY tu.id) t_sub ON t_sub.id = tu.id " +
            "ORDER BY t_sub.activeTime DESC ",
            nativeQuery = true)
    List<User> getAnchorListByActive(Integer gender, Date startTime);

    // 获取主播列表，根据系统推荐
    @Query(value = "SELECT tu.* " +
            "FROM t_user tu " +
            "INNER JOIN t_recommend tr ON tr.type = 0 AND tr.recommend = 1 AND tr.user_id = tu.id " +
            "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " +
            "ORDER BY tr.level",
            countQuery = "SELECT COUNT(*) " +
            "FROM t_user tu " +
            "INNER JOIN t_recommend tr ON tr.type = 0 AND tr.recommend = 1 AND tr.user_id = tu.id " +
            "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) ",
            nativeQuery = true)
    Page<User> getAnchorListBySystem(Integer gender, Pageable pageable);

    // 获取主播列表，根据关注排序
    @Query(value = "SELECT * FROM t_user tu " +
            "WHERE tu.identity = 1 " +
            "AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " +
            "ORDER BY tu.follows DESC ",
            countQuery = "SELECT COUNT(*) FROM t_user tu " +
                    "WHERE tu.identity = 1 " +
                    "AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL)",
            nativeQuery = true)
    Page<User> getAnchorListByFollow(Integer gender, Pageable pageable);

    // 根据推荐+关注排序
    @Query(value = "(SELECT tu.*, tr.level AS sort1, tu.follows AS sort2 FROM t_user tu " +
            "INNER JOIN t_recommend tr ON tr.type = 0 AND tr.recommend = 1 AND tr.user_id = tu.id " +
            "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) " +
            "ORDER BY tr.level) UNION ALL " +
            "(SELECT tu2.*, 10000000 AS sort1, tu2.follows AS sort2 FROM t_user tu2 " +
            "LEFT JOIN t_recommend tr2 ON tr2.type = 0 AND tr2.recommend = 1 AND tr2.user_id = tu2.id " +
            "WHERE tu2.identity = 1 AND (tu2.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND tr2.id IS NULL) " +
            "ORDER BY sort1 , sort2 DESC",
            countQuery = "SELECT COUNT(*) FROM t_user tu " +
                    "WHERE tu.identity = 1 " +
                    "AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL)",
            nativeQuery = true)
    Page<User> getAnchorListBySystemAndFollow(Integer gender, Pageable pageable);

    // 根据推荐（排班）+关注排序
    @Query(value = "(SELECT tu.*, tr.level AS sort1, tu.follows AS sort2 FROM t_user tu " +
            "INNER JOIN t_recommend tr ON tr.type = ?5 AND tr.recommend = 1 AND tr.user_id = tu.id " +
            "AND (" +
            "(IF(tr.end_time>tr.start_time, " +
            "(?1 BETWEEN tr.start_time AND tr.end_time), " +
            "((?1 BETWEEN tr.start_time AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr.end_time))) " +
            "OR tr.start_time IS NULL OR tr.end_time IS NULL)" +
            "OR " +
            "(IF(tr.end_time2>tr.start_time2, " +
            "(?1 BETWEEN tr.start_time2 AND tr.end_time2), " +
            "((?1 BETWEEN tr.start_time2 AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr.end_time2))) " +
            "OR tr.start_time2 IS NULL OR tr.end_time2 IS NULL)" +
            ") " +
            "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) " +
            "ORDER BY tr.level) UNION ALL " +
            "(SELECT tu2.*, 10000000 AS sort1, tu2.follows AS sort2 FROM t_user tu2 " +
            "LEFT JOIN t_recommend tr2 ON tr2.type = ?5 AND tr2.recommend = 1 AND tr2.user_id = tu2.id " +
            "AND (" +
            "(IF(tr2.end_time>tr2.start_time, " +
            "(?1 BETWEEN tr2.start_time AND tr2.end_time), " +
            "((?1 BETWEEN tr2.start_time AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr2.end_time))) " +
            "OR tr2.start_time IS NULL OR tr2.end_time IS NULL)" +
            "OR " +
            "(IF(tr2.end_time2>tr2.start_time2, " +
            "(?1 BETWEEN tr2.start_time2 AND tr2.end_time2), " +
            "((?1 BETWEEN tr2.start_time2 AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr2.end_time2))) " +
            "OR tr2.start_time2 IS NULL OR tr2.end_time2 IS NULL)" +
            ") " +
            "WHERE tu2.identity = 1 AND (tu2.gender LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND tr2.id IS NULL) " +
            "ORDER BY sort1 , sort2 DESC LIMIT ?3 OFFSET ?4",
//            countQuery = "SELECT COUNT(*) FROM t_user tu " +
//                    "WHERE tu.identity = 1 " +
//                    "AND (tu.gender LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL)",
            nativeQuery = true)
    List<User> getAnchorListBySystemAndFollowEx(Date date, Integer gender, int limit, int offset, int type);
//    Page<User> getAnchorListBySystemAndFollowEx(Date date, Integer gender, Pageable pageable);

    // 根据推荐（排班）+ 活跃排序
    @Query(value = "(SELECT tu.*, tr.level AS sort1, '1970-01-01 00:00:00' AS sort2 FROM t_user tu " +
            "INNER JOIN t_recommend tr ON tr.type = ?5 AND tr.recommend = 1 AND tr.user_id = tu.id " +
            "AND (" +
            "(IF(tr.end_time>tr.start_time, " +
            "(?1 BETWEEN tr.start_time AND tr.end_time), " +
            "((?1 BETWEEN tr.start_time AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr.end_time))) " +
            "OR tr.start_time IS NULL OR tr.end_time IS NULL)" +
            "OR " +
            "(IF(tr.end_time2>tr.start_time2, " +
            "(?1 BETWEEN tr.start_time2 AND tr.end_time2), " +
            "((?1 BETWEEN tr.start_time2 AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr.end_time2))) " +
            "OR tr.start_time2 IS NULL OR tr.end_time2 IS NULL)" +
            ") " +
            "WHERE tu.identity = 1 AND (tu.gender LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) " +
            "ORDER BY tr.level) UNION ALL " +
            "(SELECT tu2.*, 10000000 AS sort1, " +
            "(SELECT ta.active_time FROM t_active ta WHERE ta.user_id = tu2.id ORDER BY ta.active_time DESC LIMIT 1 OFFSET 0) AS sort2 FROM t_user tu2 " +
            "LEFT JOIN t_recommend tr2 ON tr2.type = ?5 AND tr2.recommend = 1 AND tr2.user_id = tu2.id " +
            "AND (" +
            "(IF(tr2.end_time>tr2.start_time, " +
            "(?1 BETWEEN tr2.start_time AND tr2.end_time), " +
            "((?1 BETWEEN tr2.start_time AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr2.end_time))) " +
            "OR tr2.start_time IS NULL OR tr2.end_time IS NULL)" +
            "OR " +
            "(IF(tr2.end_time2>tr2.start_time2, " +
            "(?1 BETWEEN tr2.start_time2 AND tr2.end_time2), " +
            "((?1 BETWEEN tr2.start_time2 AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr2.end_time2))) " +
            "OR tr2.start_time2 IS NULL OR tr2.end_time2 IS NULL)" +
            ") " +
            "WHERE tu2.identity = 1 AND (tu2.gender LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND tr2.id IS NULL) " +
            "ORDER BY  sort1 , sort2 DESC LIMIT ?3 OFFSET ?4",
            nativeQuery = true)
    List<User> getAnchorListBySystemAndActive(Date date, Integer gender, int limit, int offset, int type);


    @Query(value = "(SELECT tu.* FROM t_user tu " +
            "INNER JOIN t_recommend tr ON tr.type = ?2 AND tr.recommend = 1 AND tr.user_id = tu.id " +
            "AND (" +
            "(IF(tr.end_time>tr.start_time, " +
            "(?1 BETWEEN tr.start_time AND tr.end_time), " +
            "((?1 BETWEEN tr.start_time AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr.end_time))) " +
            "OR tr.start_time IS NULL OR tr.end_time IS NULL)" +
            "OR " +
            "(IF(tr.end_time2>tr.start_time2, " +
            "(?1 BETWEEN tr.start_time2 AND tr.end_time2), " +
            "((?1 BETWEEN tr.start_time2 AND '1970-01-01 23:59:59') OR (?1 BETWEEN '1970-01-01 00:00:00' AND tr.end_time2))) " +
            "OR tr.start_time2 IS NULL OR tr.end_time2 IS NULL)" +
            ") " +
            "WHERE tu.identity = 1 " +
            "ORDER BY tr.level)",
            nativeQuery = true)
    List<User> getAnchorListBySystem(Date date, int type);

    // 查询主播总数
    @Query(value = "SELECT COUNT(*) FROM t_user tu " +
            "WHERE tu.identity = 1 " +
            "AND (tu.gender LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL)", nativeQuery = true)
    Long getAnchorCount(Integer gender);

    // 获取总注册人数，给定时间内的注册人数，认证用户总人数，给定时间内认证用户人数
    @Query(value = "SELECT COUNT(*) FROM t_user UNION ALL " +
            "SELECT COUNT(*) FROM t_user WHERE create_time BETWEEN ?1 AND ?2 UNION ALL " +
            "SELECT COUNT(*) FROM t_user tu WHERE tu.identity = 1 UNION ALL " +
            "SELECT COUNT(*) FROM t_user tu WHERE tu.identity = 1 AND create_time BETWEEN ?1 AND ?2",
            nativeQuery = true)
    List<Long> userStatistics(Date start, Date end);


    // 主播数据模糊查询(错误)
    @Query(value = "SELECT tu.digit_id AS digitId, tu.nick AS nick, tu.phone AS phone, tu.gender AS gender, " +
            "tu.follows AS follows, tu.create_time AS createTime, " +
            "SUM(IF(tc.type=0,tc.duration,0)) AS audioDuration, " +
            "SUM(IF(tc.type=1,tc.duration,0)) AS videoDuration, " +
            "SUM(IF(tcb.type=0,tcb.profit_coin,0)) AS audioProfit, " +
            "SUM(IF(tcb.type=1,tcb.profit_coin,0)) AS videoProfit, " +
            "SUM(tmb.profit_coin) AS messageProfit, " +
            "SUM(tgb.profit_coin) AS giftProfit, " +
            "tas.coin As coin, " +
            "MAX(ta.active_time) AS activeTime " +
            "FROM t_user tu " +
            "LEFT JOIN t_call tc ON tc.user_id_to = tu.id " +
            "LEFT JOIN t_call_bill tcb ON tcb.user_id_to = tu.id " +
            "LEFT JOIN t_message_bill tmb ON tmb.user_id_to = tu.id " +
            "LEFT JOIN t_gift_bill tgb ON tgb.user_id_to = tu.id " +
            "LEFT JOIN t_asset tas ON tas.user_id = tu.id " +
            "LEFT JOIN t_active ta ON ta.user_id = tu.id " +
            "WHERE tu.identity = 1 AND " +
            "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
            "(tu.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
            "(tu.gender LIKE CONCAT('%', ?4, '%') OR ?4 IS NULL) AND " +
            "(tu.create_time BETWEEN ?5 AND ?6 OR ?5 IS NULL OR ?6 IS NULL) " +
            "GROUP BY tu.digit_id ORDER BY tu.digit_id DESC",
            countQuery = "SELECT COUNT(tu.id) " +
                    "FROM t_user tu " +
                    "WHERE tu.identity = 1 AND " +
                    "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
                    "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
                    "(tu.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
                    "(tu.gender LIKE CONCAT('%', ?4, '%') OR ?4 IS NULL) AND " +
                    "(tu.create_time BETWEEN ?5 AND ?6 OR ?5 IS NULL OR ?6 IS NULL) ",
            nativeQuery = true)
    Page<AnchorDto> anchorStatistics(String digitId, String nick, String phone, Integer gender,
                                     Date startTime, Date endTime, Pageable pageable);

    // 改良版主播数据模糊查询(也是错的)
    @Query(value = "SELECT tu.digitId AS digitId, tu.nick AS nick, tu.phone AS phone, tu.gender AS gender, " +
            "tu.follows AS follows, tu.createTime AS createTime, " +
            "SUM(IF(tc.type=0,tc.duration,0)) AS audioDuration, " +
            "SUM(IF(tc.type=1,tc.duration,0)) AS videoDuration, " +
            "SUM(IF(tcb.type=0,tcb.profit_coin,0)) AS audioProfit, " +
            "SUM(IF(tcb.type=1,tcb.profit_coin,0)) AS videoProfit, " +
            "SUM(tmb.profit_coin) AS messageProfit, " +
            "SUM(tgb.profit_coin) AS giftProfit, " +
            "tas.coin As coin, " +
            "MAX(ta.active_time) AS activeTime " +
            "FROM " +
            "(SELECT tu_inner.id AS id, tu_inner.digit_id AS digitId, tu_inner.nick AS nick, tu_inner.phone AS phone, " +
            "tu_inner.gender AS gender, tu_inner.follows AS follows, tu_inner.create_time AS createTime " +
            "FROM t_user tu_inner WHERE tu_inner.identity = 1 AND " +
            "(tu_inner.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu_inner.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
            "(tu_inner.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
            "(tu_inner.gender LIKE CONCAT('%', ?4, '%') OR ?4 IS NULL) AND " +
            "(tu_inner.create_time BETWEEN ?5 AND ?6 OR ?5 IS NULL OR ?6 IS NULL) " +
            "LIMIT ?7 OFFSET ?8 " +
            ")tu " +
            "LEFT JOIN t_call tc ON tc.user_id_to = tu.id " +
            "LEFT JOIN t_call_bill tcb ON tcb.user_id_to = tu.id " +
            "LEFT JOIN t_message_bill tmb ON tmb.user_id_to = tu.id " +
            "LEFT JOIN t_gift_bill tgb ON tgb.user_id_to = tu.id " +
            "LEFT JOIN t_asset tas ON tas.user_id = tu.id " +
            "LEFT JOIN t_active ta ON ta.user_id = tu.id " +
            "GROUP BY tu.digitId, tu.nick, tu.phone, tu.gender, tu.follows, tu.createTime, tas.coin " +
            "ORDER BY ?9",
            nativeQuery = true)
    List<AnchorDto> anchorStatisticsEx(String digitId, String nick, String phone, Integer gender,
                                       Date startTime, Date endTime,
                                       int limit, int offset, String order);

    // 改良版主播数据模糊查询（正确了？）
    @Query(value = "SELECT tu.digit_id AS digitId, tu.nick AS nick, tu.phone AS phone, tu.gender AS gender,tu.channel_id AS channelId, " +
            "tu.follows AS follows, tu.create_time AS createTime, " +
            "(SELECT SUM(tc.duration) FROM t_call tc WHERE tc.user_id_to = tu.id AND type = 0) AS audioDuration, " +
            "(SELECT SUM(tc.duration) FROM t_call tc WHERE tc.user_id_to = tu.id AND type = 1) AS videoDuration, " +
            "(SELECT SUM(tcb.profit_coin) FROM t_call_bill tcb WHERE tcb.user_id_to = tu.id AND type = 0) AS audioProfit, " +
            "(SELECT SUM(tcb.profit_coin) FROM t_call_bill tcb WHERE tcb.user_id_to = tu.id AND type = 1) AS videoProfit, " +
            "(SELECT SUM(tmb.profit_coin) FROM t_message_bill tmb WHERE tmb.user_id_to = tu.id) AS messageProfit, " +
            "(SELECT SUM(tgb.profit_coin) FROM t_gift_bill tgb WHERE tgb.user_id_to = tu.id) AS giftProfit, " +
            "(SELECT tas.coin FROM t_asset tas WHERE tas.user_id = tu.id) AS coin, " +
            "(SELECT MAX(ta.active_time) FROM t_active ta WHERE ta.user_id = tu.id) AS activeTime " +
            "FROM t_user tu WHERE tu.identity = 1 AND " +
            "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
            "(tu.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
            "(tu.gender LIKE CONCAT('%', ?4, '%') OR ?4 IS NULL) AND " +
            "(tu.create_time BETWEEN ?5 AND ?6 OR ?5 IS NULL OR ?6 IS NULL) AND" +
            "(tu.channel_id LIKE CONCAT('%', ?9, '%') OR ?9 IS NULL)  " +
            "ORDER BY create_time desc " +
            "LIMIT ?7 OFFSET ?8 ",
            nativeQuery = true)
    List<AnchorDto> anchorStatisticsEx2(String digitId, String nick, String phone, Integer gender,
                                       Date startTime, Date endTime,
                                       int limit, int offset, String channelId);

    // 统计主播查询的总数量
    @Query(value = "SELECT COUNT(tu.id) " +
            "FROM t_user tu " +
            "WHERE tu.identity = 1 AND " +
            "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
            "(tu.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
            "(tu.gender LIKE CONCAT('%', ?4, '%') OR ?4 IS NULL) AND " +
            "(tu.channel_id LIKE CONCAT('%', ?7, '%') OR ?7 IS NULL) AND " +
            "(tu.create_time BETWEEN ?5 AND ?6 OR ?5 IS NULL OR ?6 IS NULL) ",
    nativeQuery = true)
    Long countAnchorStatisticsEx(String digitId, String nick, String phone, Integer gender,
                                 Date startTime, Date endTime, String channelId);

    // 改良版 用户数据模糊查询（正确？）
    @Query(value = "SELECT tu.digit_id AS digitId, tu.nick AS nick, tu.phone AS phone, tu.channel_id AS channelId," +
            "tu.create_time AS createTime, " +
            "(SELECT SUM(tr.coin) FROM t_recharge tr WHERE tr.user_id = tu.id AND tr.status >= 20) AS rechargeCoin, " +
            "(SELECT SUM(tcb.origin_coin) FROM t_call_bill tcb WHERE tcb.user_id_from = tu.id AND type = 0) AS audioCoin, " +
            "(SELECT SUM(tcb.origin_coin) FROM t_call_bill tcb WHERE tcb.user_id_from = tu.id AND type = 1) AS videoCoin, " +
            "(SELECT SUM(tmb.origin_coin) FROM t_message_bill tmb WHERE tmb.user_id_from = tu.id) AS messageCoin, " +
            "(SELECT SUM(tgb.origin_coin) FROM t_gift_bill tgb WHERE tgb.user_id_from = tu.id ) AS giftCoin, " +
            "(SELECT tas.coin FROM t_asset tas WHERE tas.user_id = tu.id) AS coin, " +
            "(SELECT MAX(ta.active_time) FROM t_active ta WHERE ta.user_id = tu.id) AS activeTime, " +
            "(SELECT GROUP_CONCAT(DISTINCT(tud.app_version))  FROM t_user_device tud  where tud.user_id =tu.id) AS appVersion "+
            "FROM t_user tu " +
            "WHERE tu.identity = 0 AND " +
            "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
            "(tu.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
            "(tu.create_time BETWEEN ?4 AND ?5 OR ?4 IS NULL OR ?5 IS NULL)  AND" +
            "(tu.channel_id LIKE CONCAT('%', ?8, '%') OR ?8 IS NULL)  " +

            "ORDER BY create_time desc " +
            "LIMIT ?6 OFFSET ?7 ",
            nativeQuery = true)
    List<ConsumerDto> consumerStatisticsEx2(String digitId, String nick, String phone,
                                            Date startTime, Date endTime,
                                            int limit, int offset, String channelId);

    @Query(value = "SELECT distinct(tu.channel_id) " +
            "FROM t_user tu where  tu.channel_id is not null",nativeQuery = true)
    List<String> getBrandList();

    // 改良版 用户数据模糊查询（还是不对）
    @Query(value = "SELECT tu.digitId AS digitId, tu.nick AS nick, tu.phone AS phone, " +
            "tu.createTime AS createTime, " +
            "SUM(tr.coin) AS rechargeCoin, " +
            "SUM(IF(tcb.type=0,tcb.origin_coin,0)) AS audioCoin, " +
            "SUM(IF(tcb.type=1,tcb.origin_coin,0)) AS videoCoin, " +
            "SUM(tmb.origin_coin) AS messageCoin, " +
            "SUM(tgb.origin_coin) AS giftCoin, " +
            "tas.coin As coin, " +
            "MAX(ta.active_time) AS activeTime " +
            "FROM " +
            "(SELECT tu_inner.id, tu_inner.digit_id AS digitId, tu_inner.nick AS nick, tu_inner.phone AS phone, " +
            "tu_inner.gender AS gender, tu_inner.follows AS follows, tu_inner.create_time AS createTime " +
            "FROM t_user tu_inner WHERE tu_inner.identity = 0 AND " +
            "(tu_inner.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu_inner.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
            "(tu_inner.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
            "(tu_inner.create_time BETWEEN ?4 AND ?5 OR ?4 IS NULL OR ?5 IS NULL) " +
            "LIMIT ?6 OFFSET ?7 " +
            ")tu " +
            "LEFT JOIN t_recharge tr ON tr.user_id = tu.id AND tr.status >= 20 " +
            "LEFT JOIN t_call_bill tcb ON tcb.user_id_from = tu.id " +
            "LEFT JOIN t_message_bill tmb ON tmb.user_id_from = tu.id " +
            "LEFT JOIN t_gift_bill tgb ON tgb.user_id_from = tu.id " +
            "LEFT JOIN t_asset tas ON tas.user_id = tu.id " +
            "LEFT JOIN t_active ta ON ta.user_id = tu.id " +
            "GROUP BY tu.digitId, tu.nick, tu.phone, tu.createTime, tas.coin " +
            "ORDER BY ?8 ",
            nativeQuery = true)
    List<ConsumerDto> consumerStatisticsEx(String digitId, String nick, String phone,
                                         Date startTime, Date endTime,
                                         int limit, int offset, String order);

    // 统计用户总数量
    @Query(value = "SELECT COUNT(tu.digit_id) " +
            "FROM t_user tu " +
            "WHERE tu.identity = 0 AND " +
            "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
            "(tu.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
            "(tu.create_time BETWEEN ?4 AND ?5 OR ?4 IS NULL OR ?5 IS NULL) ",
            nativeQuery = true)
    Long countConsumerStatisticsEx(String digitId, String nick, String phone,
                                   Date startTime, Date endTime);


    // 用户数据模糊查询(不对)
    @Query(value = "SELECT tu.digit_id AS digitId, tu.nick AS nick, tu.phone AS phone, " +
            "tu.create_time AS createTime, " +
            "SUM(tr.coin) AS rechargeCoin, " +
            "SUM(IF(tcb.type=0,tcb.origin_coin,0)) AS audioCoin, " +
            "SUM(IF(tcb.type=1,tcb.origin_coin,0)) AS videoCoin, " +
            "SUM(tmb.origin_coin) AS messageCoin, " +
            "SUM(tgb.origin_coin) AS giftCoin, " +
            "tas.coin As coin, " +
            "MAX(ta.active_time) AS activeTime " +
            "FROM t_user tu " +
            "LEFT JOIN t_recharge tr ON tr.user_id = tu.id AND tr.status >= 20 " +
            "LEFT JOIN t_call_bill tcb ON tcb.user_id_from = tu.id " +
            "LEFT JOIN t_message_bill tmb ON tmb.user_id_from = tu.id " +
            "LEFT JOIN t_gift_bill tgb ON tgb.user_id_from = tu.id " +
            "LEFT JOIN t_asset tas ON tas.user_id = tu.id " +
            "LEFT JOIN t_active ta ON ta.user_id = tu.id " +
            "WHERE tu.identity = 0 AND " +
            "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
            "(tu.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
            "(tu.create_time BETWEEN ?4 AND ?5 OR ?4 IS NULL OR ?5 IS NULL) " +
            "GROUP BY tu.digit_id ORDER BY tu.digit_id DESC ",
            countQuery = "SELECT COUNT(tu.digit_id) " +
                    "FROM t_user tu " +
                    "WHERE tu.identity = 0 AND " +
                    "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
                    "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
                    "(tu.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
                    "(tu.create_time BETWEEN ?4 AND ?5 OR ?4 IS NULL OR ?5 IS NULL) ",
            nativeQuery = true)
    Page<ConsumerDto> consumerStatistics(String digitId, String nick, String phone, Date startTime, Date endTime, Pageable pageable);
    // 目前不知道如何使用参数化order by desc/asc的办法，笨方法可以写两条sql
//    List<ConsumerDto> consumerStatistics(String digitId, String nick, String phone,
//                                         Date startTime, Date endTime,
//                                         String sort, int direction,
//                                         int limit, int offset);

    // 用户消费充值统计
    @Query(value = "SELECT COUNT(DISTINCT tu.id) AS count," +
            "SUM(tr.coin) AS recharge " +
//            "SUM(tcb.origin_coin) AS voiceConsume, " +
//            "SUM(tcb2.origin_coin) AS videoConsume, " +
//            "SUM(tmb.origin_coin) AS msgConsume, " +
//            "SUM(tgb.origin_coin) AS giftConsume " +
            "FROM t_user tu " +
            "LEFT JOIN t_recharge tr ON tr.user_id = tu.id AND tr.status >= 20 " +
//            "LEFT JOIN t_call_bill tcb ON tcb.user_id_from = tu.id AND tcb.type = 0 " +
//            "LEFT JOIN t_call_bill tcb2 ON tcb2.user_id_from = tu.id AND tcb.type = 1 " +
//            "LEFT JOIN t_message_bill tmb ON tmb.user_id_from = tu.id " +
//            "LEFT JOIN t_gift_bill tgb ON tgb.user_id_from = tu.id " +
            "WHERE tu.identity = 0 AND " +
            "(tu.digit_id LIKE CONCAT('%', ?1, '%') OR ?1 IS NULL) AND " +
            "(tu.nick LIKE CONCAT('%', ?2, '%') OR ?2 IS NULL) AND " +
            "(tu.phone LIKE CONCAT('%', ?3, '%') OR ?3 IS NULL) AND " +
            "(tu.channel_id LIKE CONCAT('%', ?6, '%') OR ?6 IS NULL) AND " +
            "(tu.create_time BETWEEN ?4 AND ?5 OR ?4 IS NULL OR ?5 IS NULL) ",
            nativeQuery = true)
    CalcConsumeDto calcConsume(String digitId, String nick, String phone, Date startTime, Date endTime,String channelId);

    // 获取随机主播
    @Query(value = "SELECT * FROM t_user tu WHERE tu.identity = 1 ORDER BY RAND() LIMIT ?1", nativeQuery = true)
    List<User> findRandAnchorList(int limit);


    @Query(value = "SELECT v_profit.type, v_profit.val, v_profit.time FROM\n" +
            "(\n" +
            "    SELECT 1 AS type, SUM(tcb.profit_coin) AS val, MAX(tcb.creat_time) AS time\n" +
            "    FROM t_call_bill tcb\n" +
            "    WHERE tcb.user_id_to = ?1 AND tcb.type = 0 AND tcb.creat_time > ?2\n" +
            "    GROUP BY tcb.call_id\n" +
            "    UNION ALL\n" +
            "    SELECT 2 AS type, SUM(tcb.profit_coin) AS val, MAX(tcb.creat_time) AS time\n" +
            "    FROM t_call_bill tcb\n" +
            "    WHERE tcb.user_id_to = ?1 AND tcb.type = 1 AND tcb.creat_time > ?2\n" +
            "    GROUP BY tcb.call_id\n" +
            "    UNION ALL\n" +
            "    SELECT 3 AS type, 1.00 AS val, MAX(tcb.creat_time) AS time\n" +
            "    FROM t_call_bill tcb\n" +
            "    WHERE tcb.user_id_to = ?1 AND tcb.type = 1 AND tcb.source > 0 AND tcb.creat_time > ?2\n" +
            "    GROUP BY tcb.call_id\n" +
            "    UNION ALL\n" +
            "    SELECT 4 AS type, tgb.profit_coin AS val, tgb.creat_time AS time\n" +
            "    FROM t_gift_bill tgb\n" +
            "    WHERE tgb.user_id_to = ?1 AND tgb.creat_time > ?2\n" +
            "    UNION ALL\n" +
            "    SELECT 8 AS type, tgb2.origin_coin AS val, tgb2.creat_time AS time FROM t_gift_bill tgb2\n" +
            "    WHERE tgb2.user_id_from = ?1 AND tgb2.creat_time > ?2\n" +
            "    ) v_profit\n" +
            "ORDER BY v_profit.time DESC;", nativeQuery = true)
    Page<ProfitConsumeDetail> getAnchorProfitList(Long userId, Date date, Pageable pageable);

    @Query(value = "SELECT v_consume.type, v_consume.val, v_consume.time FROM\n" +
            "    (\n" +
            "        SELECT 5 AS type, SUM(tcb.origin_coin) AS val, MAX(tcb.creat_time) AS time\n" +
            "        FROM t_call_bill tcb\n" +
            "        WHERE tcb.user_id_from = ?1 AND tcb.type = 0 AND tcb.creat_time > ?2\n" +
            "        GROUP BY tcb.call_id\n" +
            "        UNION ALL\n" +
            "        SELECT 6 AS type, SUM(tcb.origin_coin) AS val, MAX(tcb.creat_time) AS time\n" +
            "        FROM t_call_bill tcb\n" +
            "        WHERE tcb.user_id_from = ?1 AND tcb.type = 1 AND tcb.creat_time > ?2\n" +
            "        GROUP BY tcb.call_id\n" +
            "        UNION ALL\n" +
            "        SELECT 7 AS type, 1.00 AS val, MAX(tcb.creat_time) AS time\n" +
            "        FROM t_call_bill tcb\n" +
            "        WHERE tcb.user_id_from = ?1 AND tcb.type = 1 AND tcb.source > 0 AND tcb.creat_time > ?2\n" +
            "        GROUP BY tcb.call_id\n" +
            "        UNION ALL\n" +
            "        SELECT 4 AS type, tgb.profit_coin AS val, tgb.creat_time AS time\n" +
            "        FROM t_gift_bill tgb\n" +
            "        WHERE tgb.user_id_to = ?1 AND tgb.creat_time > ?2\n" +
            "        UNION ALL\n" +
            "        SELECT 8 AS type, tgb2.origin_coin AS val, tgb2.creat_time AS time FROM t_gift_bill tgb2\n" +
            "        WHERE tgb2.user_id_from = ?1 AND tgb2.creat_time > ?2\n" +
            "    ) v_consume\n" +
            "ORDER BY v_consume.time DESC", nativeQuery = true)
    Page<ProfitConsumeDetail> getUserConsumeList(Long userId, Date date, Pageable pageable);
}
