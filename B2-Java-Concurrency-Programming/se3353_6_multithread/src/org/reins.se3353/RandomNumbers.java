package org.reins.se3353;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class RandomNumbers {
//    static Random r=new Random();
//    static ThreadLocalRandom r=ThreadLocalRandom.current();

    public static class GetRandomNumber implements Runnable{
        public void run() {
//            System.out.println(r.nextBoolean());
//            System.out.println(r.nextInt(50));
            System.out.println(ThreadLocalRandom.current().nextBoolean());
            System.out.println(ThreadLocalRandom.current().nextInt(50));
        }
    }
    public static void main(String[] args) throws Exception {
        (new Thread(new GetRandomNumber())).start();
        (new Thread(new GetRandomNumber())).start();
    }
}
