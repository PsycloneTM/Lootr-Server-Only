package net.lootr.serveronly.bench;

import net.lootr.serveronly.data.DueIndex;
import net.lootr.serveronly.data.RegionQueue;
import net.lootr.serveronly.schedule.DeadlineQueue;
import net.lootr.serveronly.schedule.LootSchedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class SchedulerBenchmark {
    private static final String DIM = "minecraft:overworld";
    private static final long FAR = 1_000_000_000L;
    private static boolean quick;
    private static long sink;

    public static void main(String[] args) {
        quick = args.length > 0 && args[0].equals("quick");
        int[] sizes = quick ? new int[] {1_000, 10_000} : new int[] {1_000, 10_000, 100_000, 500_000};

        System.out.println("Lootr scheduler benchmark (" + (quick ? "quick" : "full") + ")");
        System.out.println("JVM " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version")
                + ", " + Runtime.getRuntime().availableProcessors() + " cpus");
        System.out.println();

        header("1. Idle sweep: nothing is due (the common case between deadlines)");
        for (int n : sizes) {
            idleRegionQueue(n);
        }
        for (int n : sizes) {
            idleDueIndex(n);
        }
        for (int n : sizes) {
            idleDeadlineQueue(n);
        }

        header("2. Steady churn: 1% of entries come due each sweep and are rescheduled");
        for (int n : sizes) {
            churnRegionQueue(n);
        }
        for (int n : sizes) {
            churnDeadlineQueue(n);
        }

        header("3. Parked entries (unloaded chunks): wake one chunk");
        for (int n : sizes) {
            wakeChunk(n);
        }

        header("4. Setting change: shiftEarlier (decay/refresh duration shortened)");
        for (int n : sizes) {
            shift(n);
        }

        header("5. Bulk build and teardown");
        for (int n : sizes) {
            build(n);
        }
        System.out.println();
        System.out.println("(sink " + sink + ")");
    }

    private static void idleRegionQueue(int regions) {
        RegionQueue q = new RegionQueue();
        Map<Long, Long> naive = new HashMap<>();
        Random r = new Random(1);
        for (int i = 0; i < regions; i++) {
            long due = FAR + r.nextInt(1_000_000);
            q.put(DIM, i, due);
            naive.put((long) i, due);
        }
        long now = 1_000L;
        row("RegionQueue.takeDue", regions, time(() -> sink += q.takeDue(DIM, now).size()));
        row("  naive scan of all regions", regions, time(() -> {
            int c = 0;
            for (Map.Entry<Long, Long> e : naive.entrySet()) {
                if (e.getValue() <= now) {
                    c++;
                }
            }
            sink += c;
        }));
    }

    private static void idleDueIndex(int positions) {
        DueIndex idx = new DueIndex(p -> p >> 4);
        Map<Long, Long> naive = new HashMap<>();
        Random r = new Random(2);
        for (int i = 0; i < positions; i++) {
            long due = FAR + r.nextInt(1_000_000);
            idx.put(i, due);
            naive.put((long) i, due);
        }
        long now = 1_000L;
        row("DueIndex.takeDue", positions, time(() -> sink += idx.takeDue(now).size()));
        row("DueIndex.minDue", positions, time(() -> sink += idx.minDue()));
        row("  naive scan of all positions", positions, time(() -> {
            int c = 0;
            for (Map.Entry<Long, Long> e : naive.entrySet()) {
                if (e.getValue() <= now) {
                    c++;
                }
            }
            sink += c;
        }));
    }

    private static void idleDeadlineQueue(int carts) {
        DeadlineQueue<UUID> q = new DeadlineQueue<>();
        List<UUID> all = new ArrayList<>();
        Map<UUID, Long> naive = new HashMap<>();
        Random r = new Random(3);
        for (int i = 0; i < carts; i++) {
            UUID id = new UUID(r.nextLong(), r.nextLong());
            long due = FAR + r.nextInt(1_000_000);
            q.put(id, due);
            naive.put(id, due);
            all.add(id);
        }
        long now = 1_000L;
        row("DeadlineQueue peek+poll (carts)", carts, time(() -> {
            if (q.peekDue() <= now) {
                sink += q.pollDue(now).size();
            }
        }));
        row("  naive enumerate every cart", carts, time(() -> {
            int c = 0;
            for (UUID id : all) {
                if (naive.get(id) <= now) {
                    c++;
                }
            }
            sink += c;
        }));
    }

    private static void churnRegionQueue(int regions) {
        RegionQueue q = new RegionQueue();
        long[] dues = new long[regions];
        Map<Long, Long> naive = new HashMap<>();
        Random r = new Random(4);
        for (int i = 0; i < regions; i++) {
            dues[i] = r.nextInt(100);
            q.put(DIM, i, dues[i]);
            naive.put((long) i, dues[i]);
        }

        long[] clock = {0};
        row("RegionQueue take+reschedule", regions, time(() -> {
            clock[0] += 1;
            for (long region : q.takeDue(DIM, clock[0])) {
                q.put(DIM, region, clock[0] + 100);
            }
        }));
        long[] clock2 = {0};
        row("  naive scan + reschedule", regions, time(() -> {
            clock2[0] += 1;
            long now = clock2[0];
            for (Map.Entry<Long, Long> e : naive.entrySet()) {
                if (e.getValue() <= now) {
                    e.setValue(now + 100);
                }
            }
        }));
    }

    private static void churnDeadlineQueue(int carts) {
        DeadlineQueue<Integer> q = new DeadlineQueue<>();
        long[] naive = new long[carts];
        Random r = new Random(5);
        for (int i = 0; i < carts; i++) {
            long due = r.nextInt(100);
            q.put(i, due);
            naive[i] = due;
        }
        long[] clock = {0};
        row("DeadlineQueue poll+reschedule", carts, time(() -> {
            clock[0] += 1;
            for (int key : q.pollDue(clock[0])) {
                q.put(key, clock[0] + 100);
            }
        }));
        long[] clock2 = {0};
        row("  naive scan + reschedule", carts, time(() -> {
            clock2[0] += 1;
            long now = clock2[0];
            for (int i = 0; i < naive.length; i++) {
                if (naive[i] <= now) {
                    naive[i] = now + 100;
                }
            }
        }));
    }

    private static void wakeChunk(int positions) {
        DueIndex idx = new DueIndex(p -> p >> 4);
        Map<Long, Long> naive = new HashMap<>();
        for (int i = 0; i < positions; i++) {
            idx.put(i, LootSchedule.PARKED);
            naive.put((long) i, LootSchedule.PARKED);
        }
        long[] chunk = {0};
        long chunks = Math.max(1, positions / 16);
        row("DueIndex.wakeChunk", positions, time(() -> {
            long c = chunk[0]++ % chunks;
            sink += idx.wakeChunk(c, 5L);

            for (long p = c * 16; p < c * 16 + 16 && p < positions; p++) {
                idx.put(p, LootSchedule.PARKED);
            }
        }));
        long[] chunk2 = {0};
        row("  naive scan for parked in chunk", positions, time(() -> {
            long c = chunk2[0]++ % chunks;
            int woken = 0;
            for (Map.Entry<Long, Long> e : naive.entrySet()) {
                if ((e.getKey() >> 4) == c && e.getValue() == LootSchedule.PARKED) {
                    woken++;
                }
            }
            sink += woken;
        }));
    }

    private static void shift(int n) {
        RegionQueue q = new RegionQueue();
        DueIndex idx = new DueIndex(p -> p >> 4);
        Random r = new Random(6);
        for (int i = 0; i < n; i++) {
            q.put(DIM, i, FAR + r.nextInt(1_000_000));
            idx.put(i, FAR + r.nextInt(1_000_000));
        }
        row("RegionQueue.shiftEarlier(1)", n, time(() -> q.shiftEarlier(1)));
        row("DueIndex.shiftEarlier(1)", n, time(() -> idx.shiftEarlier(1)));
    }

    private static void build(int n) {
        Random r = new Random(7);
        long[] dues = new long[n];
        for (int i = 0; i < n; i++) {
            dues[i] = FAR + r.nextInt(1_000_000);
        }
        row("DueIndex put x N, then remove x N", n, time(() -> {
            DueIndex idx = new DueIndex(p -> p >> 4);
            for (int i = 0; i < n; i++) {
                idx.put(i, dues[i]);
            }
            for (int i = 0; i < n; i++) {
                idx.remove(i);
            }
            sink += idx.size();
        }));
        row("DueIndex.snapshot (save)", n, snapshotTime(n, dues));
    }

    private static long snapshotTime(int n, long[] dues) {
        DueIndex idx = new DueIndex(p -> p >> 4);
        for (int i = 0; i < n; i++) {
            idx.put(i, dues[i]);
        }
        return time(() -> sink += idx.snapshot()[0].length);
    }

    private static long time(Runnable op) {
        int warm = quick ? 20 : 200;
        int rounds = quick ? 5 : 9;
        long warmEnd = System.nanoTime() + (quick ? 100_000_000L : 400_000_000L);
        for (int i = 0; i < warm && System.nanoTime() < warmEnd; i++) {
            op.run();
        }

        long t0 = System.nanoTime();
        int probe = 0;
        while (System.nanoTime() - t0 < 5_000_000L && probe < 1_000_000) {
            op.run();
            probe++;
        }
        long perOp = Math.max(1, (System.nanoTime() - t0) / Math.max(1, probe));
        int iters = (int) Math.max(1, Math.min(1_000_000, 20_000_000L / perOp));
        long[] samples = new long[rounds];
        for (int r = 0; r < rounds; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < iters; i++) {
                op.run();
            }
            samples[r] = (System.nanoTime() - start) / iters;
        }
        Arrays.sort(samples);
        return samples[rounds / 2];
    }

    private static void header(String title) {
        System.out.println(title);
        System.out.printf("  %-40s %10s %14s%n", "case", "N", "median ns/op");
    }

    private static void row(String name, int n, long ns) {
        System.out.printf("  %-40s %,10d %,14d%n", name, n, ns);
    }

    private SchedulerBenchmark() {}
}
