package org.reins.se3353.se3353_4_transactionsample;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity

@Table(name = "orderitem")
public class OrderItem {
    @Id
    @Column(name = "idorderitem", nullable = false)
    private Integer id;

    @JsonBackReference
    @ManyToOne(optional = false)
    @JoinColumn(name = "orderid", nullable = false)
    private Order orderid;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Order getOrderid() {
        return orderid;
    }

    public void setOrderid(Order orderid) {
        this.orderid = orderid;
    }

}