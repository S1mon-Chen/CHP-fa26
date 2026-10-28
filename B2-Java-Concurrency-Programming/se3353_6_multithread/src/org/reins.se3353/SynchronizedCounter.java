package org.reins.se3353;

public class SynchronizedCounter {
    private int c = 0;

    public synchronized void increment() {
        c++;
        System.out.println(value());
    }

    public synchronized void decrement() {
        c--;
        System.out.println(value());
    }

    public synchronized int value() {
        return c;
    }
}

