package com.fmisser.gtc.social.repository;

import com.fmisser.gtc.social.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    List<Coupon> findByUserId(Long userId);

    List<Coupon> findByUserIdAndType(Long userId, int type);

    Coupon findByUserIdAndName(Long userId, String name);

    Coupon findByUserIdAndNameAndSource(Long userId, String name, int source);

    List<Coupon> findByUserIdAndSource(Long userId, int source);
}
