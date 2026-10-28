package org.reins.se3353;

public class Counter {
    private int c = 0;

    public void increment() {
        c++;
        System.out.println(value());
    }

    public void decrement() {
        c--;
        System.out.println(value());
    }

    public int value() {
        return c;
    }
}
