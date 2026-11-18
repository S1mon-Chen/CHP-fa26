package org.reins.se3353.auth.bean;


import jakarta.persistence.Table;
import lombok.Data;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Data
@Entity
@Table(name="user_auth")
public class Userinfo {
    @Id
    private Long id;
    private String username;
    private String password;
    private String userRole;
}
