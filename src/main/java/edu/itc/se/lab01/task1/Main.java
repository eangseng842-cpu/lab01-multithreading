package edu.itc.se.lab01.task1;

import java.util.List;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo  = new Countdown("apollo");
        Thread gemini  = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());

        List<Thread> threads = List.of(apollo, gemini, mercury, artemis);

        for (Thread t : threads) {
            if (sequential) t.run();
            else t.start();
        }

        System.out.println("Mission control: all launched");

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("Mission control: all landed");
    }
}

class Countdown extends Thread {
    public Countdown(String name) { super(name); }
    @Override
    public void run() {
        try {
            for (int i = 3; i >= 1; i--) {
                System.out.println(getName() + ": " + i);
                Thread.sleep(200);
            }
            System.out.println(getName() + ": liftoff!");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

class CountdownTask implements Runnable {
    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        try {
            for (int i = 3; i >= 1; i--) {
                System.out.println(name + ": " + i);
                Thread.sleep(200);
            }
            System.out.println(name + ": liftoff!");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}