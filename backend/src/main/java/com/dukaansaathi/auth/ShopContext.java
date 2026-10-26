package com.dukaansaathi.auth;

/** Who is logged in and which shop they are working on. Always taken from the session, never from the request. */
public record ShopContext(String userId, String userName, String shopId, String shopName, String city) {}
