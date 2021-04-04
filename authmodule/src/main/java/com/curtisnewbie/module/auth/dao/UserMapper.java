package com.curtisnewbie.module.auth.dao;

import org.apache.ibatis.annotations.Param;

/**
 * @author yongjie.zhuang
 */
public interface UserMapper {

    UserEntity findByUsername(@Param("username") String username);
}
