package com.emptyvessel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;

/**
 * Takes over ServerNameResolver#resolveAddress. The lookups are the ones the vanilla method
 * performs; the only change is that an SRV redirect with several records is probed in RFC 2782
 * order instead of trusting whichever record the resolver returned first.
 */
public final class SrvResolver {
    private static final int CONNECT_TIMEOUT_MS = 800;
    private static final long PROBE_BUDGET_NS = 3_000_000_000L;
    private static final int CONCURRENCY = 2;
    private static final ConcurrentMap<String, ResolvedServerAddress> selections = new ConcurrentHashMap<>();

    private SrvResolver() {
    }

    public static Optional<ResolvedServerAddress> resolve(ServerAddress address, ServerAddressResolver resolver,
                                                          AddressCheck check) {
        String key = key(address);
        ResolvedServerAddress cached = selections.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ResolvedServerAddress> resolved = resolveUncached(address, resolver, check);
        // Failures stay uncached so a transient DNS error retries exactly like vanilla does.
        resolved.ifPresent(value -> selections.putIfAbsent(key, value));
        return resolved;
    }

    /** Called before a list ping so the next ping re-probes instead of replaying the old choice. */
    public static void refresh(ServerAddress address) {
        selections.remove(key(address));
    }

    private static Optional<ResolvedServerAddress> resolveUncached(ServerAddress address, ServerAddressResolver resolver,
                                                                   AddressCheck check) {
        Optional<ResolvedServerAddress> direct = resolver.resolve(address);
        boolean directAllowed = direct.isPresent() && check.isAllowed(direct.get());
        if (!directAllowed && !check.isAllowed(address)) {
            return Optional.empty();
        }
        if (address.getPort() != 25565) {
            return direct;
        }
        List<SrvRecords.Entry> entries = SrvRecords.lookup(address.getHost());
        if (entries.isEmpty()) {
            return direct;
        }
        List<SrvRecords.Entry> ordered = SrvRecords.ordered(entries);
        Optional<ResolvedServerAddress> redirected = ordered.size() == 1
                ? resolver.resolve(new ServerAddress(ordered.get(0).target(), ordered.get(0).port()))
                        .filter(check::isAllowed)
                : probe(ordered, resolver, check);
        // A redirect that leads nowhere must not strand a server whose direct record still works.
        return redirected.isPresent() ? redirected : direct;
    }

    /**
     * Probes the candidates with two workers pulling from the same priority-ordered list, so a slow
     * first choice does not add its timeout to the second one.
     */
    private static Optional<ResolvedServerAddress> probe(List<SrvRecords.Entry> ordered,
                                                         ServerAddressResolver resolver, AddressCheck check) {
        int workers = Math.min(CONCURRENCY, ordered.size());
        AtomicInteger next = new AtomicInteger();
        AtomicBoolean settled = new AtomicBoolean();
        ConcurrentMap<Integer, ResolvedServerAddress> resolved = new ConcurrentHashMap<>();
        BlockingQueue<ResolvedServerAddress> winner = new LinkedBlockingQueue<>();
        CountDownLatch finished = new CountDownLatch(workers);
        ExecutorService pool = Executors.newFixedThreadPool(workers, task -> {
            Thread thread = new Thread(task, "serverresolvefix-probe");
            thread.setDaemon(true);
            return thread;
        });
        try {
            for (int i = 0; i < workers; i++) {
                pool.execute(() -> {
                    try {
                        for (int index = next.getAndIncrement(); index < ordered.size() && !settled.get();
                             index = next.getAndIncrement()) {
                            SrvRecords.Entry entry = ordered.get(index);
                            Optional<ResolvedServerAddress> candidate = resolver
                                    .resolve(new ServerAddress(entry.target(), entry.port()))
                                    .filter(check::isAllowed);
                            if (candidate.isEmpty()) {
                                continue;
                            }
                            resolved.put(index, candidate.get());
                            if (reachable(candidate.get().asInetSocketAddress())) {
                                winner.offer(candidate.get());
                                return;
                            }
                        }
                    } finally {
                        finished.countDown();
                    }
                });
            }
            long deadline = System.nanoTime() + PROBE_BUDGET_NS;
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                ResolvedServerAddress found = winner.poll(Math.min(remaining / 1_000_000L, 200L), TimeUnit.MILLISECONDS);
                if (found != null) {
                    return Optional.of(found);
                }
                if (finished.getCount() == 0) {
                    break;
                }
            }
            // Nothing answered: keep the highest-priority address so the failure reads like vanilla's.
            return resolved.entrySet().stream()
                    .min(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            settled.set(true);
            pool.shutdownNow();
        }
    }

    private static boolean reachable(InetSocketAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(address, CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String key(ServerAddress address) {
        return address.getHost().toLowerCase(Locale.ROOT) + ":" + address.getPort();
    }
}
