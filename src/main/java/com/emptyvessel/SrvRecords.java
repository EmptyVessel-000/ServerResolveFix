package com.emptyvessel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

/** Reads every SRV record for a host and orders them the way RFC 2782 asks. */
public final class SrvRecords {
    private SrvRecords() {
    }

    public record Entry(int priority, int weight, int port, String target) {
    }

    public static List<Entry> lookup(String host) {
        List<Entry> entries = new ArrayList<>();
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext context = null;
        try {
            context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
            Attribute attribute = attributes.get("srv");
            if (attribute == null) {
                return entries;
            }
            for (NamingEnumeration<?> values = attribute.getAll(); values.hasMore(); ) {
                String[] parts = String.valueOf(values.next()).split(" ");
                if (parts.length < 4) {
                    continue;
                }
                entries.add(new Entry(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), parts[3]));
            }
        } catch (Throwable ignored) {
            // A broken lookup means "no SRV" here, matching what the vanilla redirect handler does.
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {
                }
            }
        }
        return entries;
    }

    /** Lowest priority first; inside one priority, weighted order. */
    public static List<Entry> ordered(List<Entry> entries) {
        Map<Integer, List<Entry>> byPriority = new TreeMap<>();
        for (Entry entry : entries) {
            byPriority.computeIfAbsent(entry.priority(), key -> new ArrayList<>()).add(entry);
        }
        List<Entry> ordered = new ArrayList<>();
        for (List<Entry> group : byPriority.values()) {
            ordered.addAll(weightedOrder(group));
        }
        return ordered;
    }

    private static List<Entry> weightedOrder(List<Entry> group) {
        List<Entry> remaining = new ArrayList<>(group);
        List<Entry> ordered = new ArrayList<>();
        while (!remaining.isEmpty()) {
            int total = 0;
            for (Entry entry : remaining) {
                total += entry.weight();
            }
            if (total == 0) {
                Collections.shuffle(remaining);
                ordered.addAll(remaining);
                break;
            }
            int pick = ThreadLocalRandom.current().nextInt(total) + 1;
            int running = 0;
            for (int i = 0; i < remaining.size(); i++) {
                running += remaining.get(i).weight();
                if (running >= pick) {
                    ordered.add(remaining.remove(i));
                    break;
                }
            }
        }
        return ordered;
    }
}

