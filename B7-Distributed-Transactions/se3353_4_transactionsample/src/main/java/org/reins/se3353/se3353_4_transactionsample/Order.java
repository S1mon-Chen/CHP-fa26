package org.reins.se3353.se3353_4_transactionsample;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "`order`")
public class Order {
    @Id
    @Column(name = "idorder", nullable = false)
    private Integer id;

    @JsonManagedReference
    @OneToMany(mappedBy = "orderid")
    private Set<OrderItem> orderitems = new LinkedHashSet<>();

    public Set<OrderItem> getOrderitems() {
        return orderitems;
    }

    public void setOrderitems(Set<OrderItem> orderitems) {
        this.orderitems = orderitems;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    //TODO [Reverse Engineering] generate columns from DB
}