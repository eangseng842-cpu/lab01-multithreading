package edu.itc.se.lab01.task2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

interface Counter {
    void increment();
    long get();
}

public class Bench {
    static final int THREADS = 8;
    static final int ITERATIONS = 1_000_000;

    static void run(String label, Counter counter) throws InterruptedException {
        long start = System.nanoTime();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < ITERATIONS; i++) counter.increment();
            }, "inc-" + t);
            threads.add(th);
            th.start();
        }
        for (Thread th : threads) th.join();
        long ms = (System.nanoTime() - start) / 1_000_000;
        long expected = (long) THREADS * ITERATIONS;
        System.out.printf("%-22s value=%,d  correct=%-5b  %d ms%n",
                label, counter.get(), counter.get() == expected, ms);
    }

    public static void main(String[] args) throws InterruptedException {
     
        System.out.println("--- Running Unsafe ---");
        run("UnsafeCounter", new UnsafeCounter());
        run("UnsafeCounter", new UnsafeCounter());
        run("UnsafeCounter", new UnsafeCounter());

        System.out.println("\n--- Running Safe Counters ---");
        run("SynchronizedCounter", new SynchronizedCounter());
        run("LockCounter", new LockCounter());
        run("AtomicCounter", new AtomicCounter());
      
        run("LongAdderCounter", new LongAdderCounter());
    }
}

class UnsafeCounter implements Counter {
    private long count = 0;
    @Override public void increment() { count++; }
    @Override public long get() { return count; }
}

class SynchronizedCounter implements Counter {
    private long count = 0;
    @Override public synchronized void increment() { count++; }
    @Override public synchronized long get() { return count; }
}

class LockCounter implements Counter {
    private long count = 0;
    private final ReentrantLock lock = new ReentrantLock();
    
    @Override 
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock(); // Always release in finally block!
        }
    }
    
    @Override 
    public long get() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}

class AtomicCounter implements Counter {
    private final AtomicLong count = new AtomicLong();
    @Override public void increment() { count.incrementAndGet(); }
    @Override public long get() { return count.get(); }
}

class LongAdderCounter implements Counter {
    private final LongAdder count = new LongAdder();
    @Override public void increment() { count.increment(); }
    @Override public long get() { return count.sum(); }
}