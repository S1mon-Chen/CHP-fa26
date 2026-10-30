package org.reins.se3353.se3353_3_websockettransfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank")
public class Bank {
    @Id
    @Column(name = "account", nullable = false, length = 45)
    private String id;

    @Column(name = "balance")
    private Integer balance;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getBalance() {
        return balance;
    }

    public void setBalance(Integer balance) {
        this.balance = balance;
    }

}