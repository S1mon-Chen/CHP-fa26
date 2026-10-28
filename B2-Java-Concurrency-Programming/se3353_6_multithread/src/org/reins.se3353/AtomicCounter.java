package org.reins.se3353;

import java.util.concurrent.atomic.AtomicInteger;

class AtomicCounter {
    private AtomicInteger c = new AtomicInteger(0);

    public void increment() {
//        c.incrementAndGet();
        System.out.println(c.incrementAndGet());
    }

    public void decrement() {
//        c.decrementAndGet();
        System.out.println(c.incrementAndGet());
    }

    public int value() {
        return c.get();
    }

}
