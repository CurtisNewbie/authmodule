package com.curtisnewbie.module.auth.dao;

/**
 * Basic info of user
 *
 * @author yongjie.zhuang
 */
public class UserInfo {

    private Integer id;

    /**
     * username
     */
    private String username;

    /**
     * role
     */
    private String role;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
