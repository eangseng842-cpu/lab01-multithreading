# Lab-01: Multithreading in Java

## Task 1: Threads vs Virtual Threads
- **`start()` vs `run()`:** Calling `run()` executes the code sequentially on the `main` thread, whereas calling `start()` spawns a completely new concurrent thread.
- **Platform vs Virtual Threads:** Platform threads map 1:1 to heavy OS threads. Virtual threads are lightweight, managed by the JVM, and mount/unmount from carrier threads, allowing millions of them to run concurrently without exhausting OS resources.

## Task 2: Thread Safety and Counters
- **UnsafeCounter:** Failed to reach the expected 8,000,000 count because the `count++` operation is not atomic (read-modify-write). Multiple threads overwrote each other's updates (Race Condition).
- **Safe Counters:** `SynchronizedCounter`, `LockCounter`, `AtomicCounter`, and `LongAdderCounter` all safely reached 8,000,000. `LongAdder` generally performs the best under high contention because it maintains separate variables for different threads to reduce contention, summing them only when `get()` is called.

## Task 3: Producer-Consumer
- **wait() / notifyAll():** Requires manual synchronization blocks and careful handling of full/empty states to prevent spurious wakeups and deadlocks.
- **ArrayBlockingQueue:** Vastly simplifies the producer-consumer pattern by handling the locking, waiting, and waking up internally.

## Task 4: Thread Pools and Futures
### I/O-Bound Performance: Platform vs. Virtual Threads
- **Platform Pool (20 threads):** Takes over 5 seconds for 500 tasks. Because `Thread.sleep()` blocks the underlying OS thread, only 20 tasks can sleep at a time. The bottleneck is thread starvation.
- **Virtual Threads:** Takes just over 1 second. When a virtual thread hits a blocking operation like `sleep()`, it unmounts from its carrier OS thread, freeing the OS thread to execute other virtual threads. This allows all 500 tasks to wait concurrently, resulting in massive throughput gains for I/O-bound workloads.

## Task 5: Diagnosing a Deadlock
### Deadlock Analysis
- **The Bug:** `runBrokenBank()` suffers from a circular wait dependency. If Thread A locks Account 1 to transfer to Account 2, and Thread B simultaneously locks Account 2 to transfer to Account 1, both block endlessly waiting for the other's lock.
- **Fix A (OrderedBank):** Deadlock is prevented by global lock ordering. By strictly locking the account with the lowest `id` first, circular waits are mathematically impossible.
- **Fix B (TryLockBank):** Deadlock is avoided via timeouts. Using `tryLock(10, MILLISECONDS)`, a thread releases its held lock and backs off randomly if it cannot acquire both locks, preventing livelocks.
- **Graceful Shutdown:** Workers operate in a loop checking `!Thread.currentThread().isInterrupted()`. When `main` interrupts them, any blocked thread throws an `InterruptedException`. Workers catch it, restore the interrupt flag, and exit safely.