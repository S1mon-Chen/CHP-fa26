package org.reins.se3353;

public class CreateNamedThreadsWithBuilders {
    public static void main(String[] args) {

        try {
            Thread.Builder builder =
                    Thread.ofVirtual().name("worker-", 0);

            Runnable task = () -> {
                System.out.println("Thread ID: " +
                        Thread.currentThread().threadId());
            };

            // name "worker-0"
            Thread t1 = builder.start(task);
            t1.join();
            System.out.println(t1.getName() + " terminated");

            // name "worker-1"
            Thread t2 = builder.start(task);
            t2.join();
            System.out.println(t2.getName() + " terminated");

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
