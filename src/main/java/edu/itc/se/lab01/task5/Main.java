package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class Main {

    static final class Account {
        final int id;
        private long balance;
        final ReentrantLock lock = new ReentrantLock(); // Added for TryLockBank

        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    public static void main(String[] args) throws InterruptedException {
        startWatchdog();

        // Uncomment ONE of these to run the respective test:
        runBrokenBank();
        // runOrderedBank();
        // runTryLockBank();
    }

    // --- STEP 3: Watchdog ---
    static void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            while (!Thread.currentThread().isInterrupted()) {
                long[] deadlockedIds = bean.findDeadlockedThreads();
                if (deadlockedIds != null) {
                    ThreadInfo[] infos = bean.getThreadInfo(deadlockedIds);
                    System.out.print("[watchdog] DEADLOCK detected between: ");
                    String names = Arrays.stream(infos)
                            .map(ThreadInfo::getThreadName)
                            .reduce((a, b) -> a + ", " + b).orElse("");
                    System.out.println(names);
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();
    }

    // --- STEP 1 & 2: Broken Bank (Deadlock) ---
    static void runBrokenBank() throws InterruptedException {
        System.out.println("=== Running Broken Bank ===");
        Account[] accounts = createAccounts();
        Thread[] workers = new Thread[8];

        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 100_000; i++) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        // BUG: Circular wait dependency
                        synchronized (a) {
                            synchronized (b) {
                                if (a.balance() >= 10) { a.withdraw(10); b.deposit(10); }
                            }
                        }
                    }
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
        printTotal(accounts);
    }

    // --- STEP 4 & 6: OrderedBank with Graceful Shutdown ---
    static void runOrderedBank() throws InterruptedException {
        System.out.println("=== Running OrderedBank (Graceful Shutdown) ===");
        Account[] accounts = createAccounts();
        Thread[] workers = new Thread[8];
        AtomicLong transferCount = new AtomicLong(0);
        long start = System.currentTimeMillis();

        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                long localTransfers = 0;
                // Graceful Shutdown loop condition[cite: 10]
                while (!Thread.currentThread().isInterrupted()) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        // FIX A: Lock ordering by ID to prevent circular wait[cite: 10]
                        Account first = a.id < b.id ? a : b;
                        Account second = a.id < b.id ? b : a;

                        synchronized (first) {
                            synchronized (second) {
                                if (a.balance() >= 10) {
                                    a.withdraw(10);
                                    b.deposit(10);
                                    localTransfers++;
                                }
                            }
                        }
                    }
                }
                transferCount.addAndGet(localTransfers);
                System.out.println(Thread.currentThread().getName() + " stopped after " + localTransfers + " transfers");
            }, "transfer-" + w);
            workers[w].start();
        }

        // Graceful shutdown sequence[cite: 10]
        Thread.sleep(2000);
        System.out.println("[main] interrupting 8 workers after 2000 ms");
        for (Thread t : workers) t.interrupt();
        
        boolean allStopped = true;
        for (Thread t : workers) {
            t.join(1000);
            if (t.isAlive()) allStopped = false;
        }

        long time = System.currentTimeMillis() - start;
        System.out.printf("all workers stopped=%b transfers=%d in %d ms%n", allStopped, transferCount.get(), time);
        printTotal(accounts);
    }

    // --- STEP 5: TryLockBank ---
    static void runTryLockBank() throws InterruptedException {
        System.out.println("=== Running TryLockBank ===");
        Account[] accounts = createAccounts();
        Thread[] workers = new Thread[8];
        AtomicInteger retries = new AtomicInteger(0);
        long start = System.currentTimeMillis();

        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 100_000; i++) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a == b) continue;

                    while (true) {
                        try {
                            // FIX B: tryLock with timeout[cite: 10]
                            if (a.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                                try {
                                    if (b.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                                        try {
                                            if (a.balance() >= 10) { a.withdraw(10); b.deposit(10); }
                                            break; // Success, exit retry loop
                                        } finally {
                                            b.lock.unlock();
                                        }
                                    }
                                } finally {
                                    a.lock.unlock(); // Release and back-off to prevent livelock
                                }
                            }
                            // Random back-off
                            retries.incrementAndGet();
                            Thread.sleep(rnd.nextInt(1, 10));
                        } catch (InterruptedException e) {
                            // Restore interrupt flag[cite: 10]
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }, "transfer-" + w);
            workers[w].start();
        }

        for (Thread t : workers) t.join();
        long time = System.currentTimeMillis() - start;
        System.out.printf("TryLockBank: transfers=800000 retries=%d in %d ms%n", retries.get(), time);
        printTotal(accounts);
    }

    private static Account[] createAccounts() {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);
        return accounts;
    }

    private static void printTotal(Account[] accounts) {
        long total = 0;
        for (Account a : accounts) total += a.balance();
        System.out.println("total = " + total + " (expected 4000)");
    }
}