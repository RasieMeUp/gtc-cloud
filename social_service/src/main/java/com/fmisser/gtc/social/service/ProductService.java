package com.fmisser.gtc.social.service;

import com.fmisser.gtc.base.exception.ApiException;
import com.fmisser.gtc.social.domain.Product;

import java.util.List;

public interface ProductService {
    List<Product> getIapProductList() throws ApiException;
    Product getValidProduct(Long productId) throws ApiException;
    Product getProductByName(String name) throws ApiException;
}
