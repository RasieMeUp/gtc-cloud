package com.fmisser.gtc.social.utils;

import com.fmisser.gtc.base.prop.OssConfProp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.*;

class MinioUtilsTest {

    static MinioUtils minioUtils = null;

    @BeforeAll
    static void init() {
        OssConfProp ossConfProp = new OssConfProp();
        minioUtils = new MinioUtils(ossConfProp);
        minioUtils.init();
    }

    @AfterAll
    static void destroy() {

    }

    @BeforeEach
    void setUp() {

    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void bucketExists() {
        String bucketName = "test-bucket";
        boolean exist = minioUtils.bucketExists(bucketName);
        Assertions.assertTrue(exist);
    }

    @Test
    void createBucket() {
        String newBucketName = "test-bucket-2";
        Assertions.assertFalse(minioUtils.bucketExists(newBucketName));
        minioUtils.createBucket(newBucketName);
        Assertions.assertTrue(minioUtils.bucketExists(newBucketName));
    }

    @Test
    void getAllBuckets() {
    }

    @Test
    void getBucketPolicy() {
        String bucketPolicy = minioUtils.getBucketPolicy("system-config");
        System.out.println(bucketPolicy);
    }

    @Test
    void upload() {
    }

    @Test
    void put() {
    }

    @Test
    void delete() {
    }

    @Test
    void getObjectUrl() {
        String bucketName = "test-bucket";
        String objectName = "images/tp1.png";
        String returnUrl = minioUtils.getObjectUrl(bucketName, objectName);
        System.out.println("object url is:" + returnUrl);
        Assertions.assertNotNull(returnUrl);
    }
}