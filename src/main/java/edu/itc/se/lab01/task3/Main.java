package edu.itc.se.lab01.task3;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

record Order(int id, String item, int qty) {}

interface OrderQueue {
    void put(Order order) throws InterruptedException;
    Order take() throws InterruptedException;
}

class BoundedBuffer implements OrderQueue {
    private final java.util.ArrayDeque<Order> items = new java.util.ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public synchronized void put(Order order) throws InterruptedException {
        // Must use 'while' to re-check condition upon waking up
        while (items.size() == capacity) {
            wait();
        }
        items.addLast(order);
        notifyAll(); // Wake up consumers (and other producers)
    }

    @Override
    public synchronized Order take() throws InterruptedException {
        // Must use 'while' to prevent reading from empty buffer
        while (items.isEmpty()) {
            wait();
        }
        Order order = items.removeFirst();
        notifyAll(); // Wake up producers (and other consumers)
        return order;
    }
}

// Adapter for java.util.concurrent queue
final class BlockingOrderQueue implements OrderQueue {
    private final java.util.concurrent.BlockingQueue<Order> q;
    BlockingOrderQueue(int capacity) { 
        this.q = new java.util.concurrent.ArrayBlockingQueue<>(capacity); 
    }
    public void put(Order o) throws InterruptedException { q.put(o); }
    public Order take() throws InterruptedException { return q.take(); }
}

class Producer implements Runnable {
    private final OrderQueue queue;
    private final int numOrders;
    private final int poisonPills;

    public Producer(OrderQueue queue, int numOrders, int poisonPills) {
        this.queue = queue;
        this.numOrders = numOrders;
        this.poisonPills = poisonPills;
    }

    @Override
    public void run() {
        try {
            for (int i = 1; i <= numOrders; i++) {
                Thread.sleep((long) (Math.random() * 21)); // 0-20 ms pause
                queue.put(new Order(i, "item-" + i, (int) (Math.random() * 5) + 1));
            }
            // Put poison pills at the end
            for (int i = 0; i < poisonPills; i++) {
                queue.put(new Order(-1, "poison", 0));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class Consumer implements Runnable {
    private final OrderQueue queue;
    private final AtomicInteger counter;

    public Consumer(OrderQueue queue, AtomicInteger counter) {
        this.queue = queue;
        this.counter = counter;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Order order = queue.take();
                if (order.id() == -1) {
                    System.out.println(Thread.currentThread().getName() + " received poison pill, stopping");
                    break;
                }
                Thread.sleep(5 + (long) (Math.random() * 26)); // 5-30 ms pause
                System.out.println(Thread.currentThread().getName() + " processed " + order);
                counter.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Running with custom BoundedBuffer ===");
        runSimulation(new BoundedBuffer(5));
    }

    static void runSimulation(OrderQueue buffer) throws InterruptedException {
        long start = System.currentTimeMillis();
        AtomicInteger processedCount = new AtomicInteger(0);
        List<Thread> threads = new ArrayList<>();

        // 3 Consumers
        for (int i = 1; i <= 3; i++) {
            Thread c = new Thread(new Consumer(buffer, processedCount), "consumer-" + i);
            threads.add(c);
            c.start();
        }

        // 2 Producers (We assign the 3 poison pills to Producer 1 so we don't spam extras)
        Thread p1 = new Thread(new Producer(buffer, 50, 3), "producer-1");
        Thread p2 = new Thread(new Producer(buffer, 50, 0), "producer-2");
        threads.add(p1);
        threads.add(p2);
        p1.start();
        p2.start();

        for (Thread t : threads) {
            t.join();
        }

        long time = System.currentTimeMillis() - start;
        System.out.printf("Total processed: %d (expected 100) in %d ms%n", processedCount.get(), time);
        
        if (processedCount.get() != 100) {
            System.err.println("ERROR: Processed count is incorrect!");
        }
    }
}

class Main2 {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Running with ArrayBlockingQueue ===");
        Main.runSimulation(new BlockingOrderQueue(5));
    }
}