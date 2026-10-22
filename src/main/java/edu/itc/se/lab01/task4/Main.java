package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {
    static long countPrimes(long from, long to) {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long d = 2; d * d <= n; d++) {
                if (n % d == 0) { prime = false; break; }
            }
            if (prime) count++;
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        final long LIMIT = 4_000_000, CHUNKS = 16;

        // --- 1. CPU-Bound Part ---
        long t0 = System.nanoTime();
        long single = countPrimes(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n", single, (System.nanoTime() - t0) / 1_000_000);

        t0 = System.nanoTime();
        int cores = Runtime.getRuntime().availableProcessors();
        try (ExecutorService pool = Executors.newFixedThreadPool(cores)) {
            List<Future<Long>> futures = new ArrayList<>();
            long chunkSize = LIMIT / CHUNKS;
            
            for (int i = 0; i < CHUNKS; i++) {
                long from = i * chunkSize;
                long to = (i == CHUNKS - 1) ? LIMIT : (i + 1) * chunkSize;
                futures.add(pool.submit(new PrimeCounter(from, to)));
            }
            
            long total = 0;
            for (Future<Long> f : futures) {
                total += f.get();
            }
            System.out.printf("pool (%d threads): %d primes in %d ms%n", cores, total, (System.nanoTime() - t0) / 1_000_000);
        }

        // --- 2. I/O-Bound Part ---
        System.out.println("\n--- I/O Simulation ---");
        try (ExecutorService fixedPool = Executors.newFixedThreadPool(20)) {
            runDownloads("fixed(20) platform threads", fixedPool);
        }
        
        try (ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloads("virtual threads", virtualPool);
        }
    }

    static void runDownloads(String label, ExecutorService executor) {
        long t0 = System.nanoTime();
        List<Future<String>> futures = new ArrayList<>();
        
        for (int i = 0; i < 500; i++) {
            futures.add(executor.submit(new FakeDownload(i)));
        }

        int ok = 0, failed = 0, timedOut = 0;
        
        for (Future<String> f : futures) {
            try {
                // 4. Timeouts: wait up to 1 second
                f.get(1, TimeUnit.SECONDS);
                ok++;
            } catch (ExecutionException e) {
                // 3. Failure handling
                failed++;
            } catch (TimeoutException e) {
                timedOut++;
                f.cancel(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("downloads on %-28s : ok=%d failed=%d timedOut=%d in %d ms%n", 
                label, ok, failed, timedOut, ms);
    }
}

class PrimeCounter implements Callable<Long> {
    private final long from;
    private final long to;

    public PrimeCounter(long from, long to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Long call() {
        return Main.countPrimes(from, to);
    }
}

class FakeDownload implements Callable<String> {
    private final int id;

    public FakeDownload(int id) {
        this.id = id;
    }

    @Override
    public String call() throws Exception {
        if (id % 50 == 0) {
            throw new IOException("boom");
        }
        if (id == 99) { // One specific task hangs
            Thread.sleep(5000);
        } else {
            Thread.sleep(200);
        }
        return "url-" + id + " -> 1024";
    }
}