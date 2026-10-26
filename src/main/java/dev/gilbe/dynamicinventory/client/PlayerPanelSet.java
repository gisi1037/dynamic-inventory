package dev.gilbe.dynamicinventory.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Named presentation groups over stable ordinary storage indices. Never contains stacks or container IDs. */
public final class PlayerPanelSet {

    public static final String MAIN = "main";
    public static final PlayerPanelSet DEFAULT = new PlayerPanelSet(
        Collections.singletonList(
            new Entry(MAIN, "Main", Collections.emptyList(), PlayerPanelProfile.DEFAULT, Collections.emptyMap())));
    public final List<Entry> entries;

    public PlayerPanelSet(List<Entry> entries) {
        if (entries.isEmpty() || entries.size() > 64 || !MAIN.equals(entries.get(0).id))
            throw new IllegalArgumentException("Main panel required");
        Set<String> ids = new HashSet<>();
        Set<Integer> slots = new HashSet<>();
        for (Entry entry : entries) {
            if (!ids.add(entry.id)) throw new IllegalArgumentException("Duplicate panel ID");
            for (int slot : entry.slots)
                if (!slots.add(slot)) throw new IllegalArgumentException("Duplicate panel slot");
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public Entry get(String id) {
        for (Entry entry : entries) if (entry.id.equals(id)) return entry;
        throw new IllegalArgumentException("Unknown panel");
    }

    /** Main owns every index not reserved by another panel, including inventory upgrades acquired later. */
    public List<Integer> members(String id, int capacity) {
        if (!MAIN.equals(id)) {
            List<Integer> active = new ArrayList<>();
            for (int slot : get(id).slots) if (slot < capacity) active.add(slot);
            return active;
        }
        Set<Integer> reserved = new HashSet<>();
        for (int i = 1; i < entries.size(); i++) reserved.addAll(entries.get(i).slots);
        List<Integer> main = new ArrayList<>();
        for (int i = 0; i < capacity; i++) if (!reserved.contains(i)) main.add(i);
        return main;
    }

    public PlayerPanelSet replace(Entry value) {
        List<Entry> next = new ArrayList<>(entries);
        for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(value.id)) next.set(i, value);
        return new PlayerPanelSet(next);
    }

    public PlayerPanelSet add() {
        List<Entry> next = new ArrayList<>(entries);
        next.add(
            new Entry(
                UUID.randomUUID()
                    .toString(),
                "Panel " + (entries.size() + 1),
                Collections.emptyList(),
                new PlayerPanelProfile(true, 124, 118, 10000, 5000),
                Collections.emptyMap()));
        return new PlayerPanelSet(next);
    }

    public PlayerPanelSet remove(String id) {
        if (MAIN.equals(id)) throw new IllegalArgumentException("Main panel cannot be removed");
        List<Entry> next = new ArrayList<>(entries);
        next.removeIf(entry -> entry.id.equals(id));
        return new PlayerPanelSet(next);
    }

    /** Retain unchanged memberships, release edited excess, then satisfy growth from the free pool. */
    public PlayerPanelSet allocate(int[] counts, int capacity) {
        if (counts.length != entries.size()) throw new IllegalArgumentException("Missing panel counts");
        long total = 0;
        for (int count : counts) {
            if (count < 0) throw new IllegalArgumentException("Negative panel size");
            total += count;
        }
        if (total != capacity) throw new IllegalArgumentException("Allocate exactly " + capacity + " storage slots");
        List<List<Integer>> kept = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            List<Integer> slots = new ArrayList<>();
            if (i > 0) {
                for (int slot : entries.get(i).slots) if (slot < capacity && slots.size() < counts[i]) {
                    slots.add(slot);
                    used.add(slot);
                }
            }
            kept.add(slots);
        }
        int free = 0;
        for (int i = 1; i < entries.size(); i++) {
            List<Integer> slots = kept.get(i);
            while (slots.size() < counts[i]) {
                while (used.contains(free)) free++;
                slots.add(free);
                used.add(free++);
            }
            // Shrinking capacity hides reservations rather than silently reassigning them to another panel.
            for (int slot : entries.get(i).slots) if (slot >= capacity) slots.add(slot);
        }
        List<Entry> next = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) next.add(
            entries.get(i)
                .withSlots(kept.get(i)));
        return new PlayerPanelSet(next);
    }

    public JsonArray toJson() {
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            JsonObject value = new JsonObject(), overrides = new JsonObject();
            value.addProperty("id", entry.id);
            value.addProperty("name", entry.name);
            JsonArray slots = new JsonArray();
            for (int slot : entry.slots) slots.add(new com.google.gson.JsonPrimitive(slot));
            value.add("slots", slots);
            value.add("global", entry.global.toJson());
            for (Map.Entry<String, PlayerPanelProfile> override : entry.overrides.entrySet()) overrides.add(
                override.getKey(),
                override.getValue()
                    .toJson());
            value.add("interfaces", overrides);
            array.add(value);
        }
        return array;
    }

    public static PlayerPanelSet fromJson(JsonArray array) {
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            if (object.entrySet()
                .size() != 5) throw new IllegalArgumentException("Invalid panel fields");
            List<Integer> slots = new ArrayList<>();
            for (JsonElement slot : object.getAsJsonArray("slots")) {
                if (!slot.isJsonPrimitive() || !slot.getAsJsonPrimitive()
                    .isNumber()
                    || !slot.getAsString()
                        .matches("0|[1-9][0-9]*"))
                    throw new IllegalArgumentException("Invalid slot index");
                slots.add(slot.getAsInt());
            }
            Map<String, PlayerPanelProfile> overrides = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("interfaces")
                .entrySet()) {
                PlayerPanelProfiles.validateKey(entry.getKey());
                overrides.put(
                    entry.getKey(),
                    PlayerPanelProfile.fromJson(
                        entry.getValue()
                            .getAsJsonObject()));
            }
            entries.add(
                new Entry(
                    object.get("id")
                        .getAsString(),
                    object.get("name")
                        .getAsString(),
                    slots,
                    PlayerPanelProfile.fromJson(object.getAsJsonObject("global")),
                    overrides));
        }
        return new PlayerPanelSet(entries);
    }

    public static final class Entry {

        public final String id, name;
        public final List<Integer> slots;
        public final PlayerPanelProfile global;
        public final Map<String, PlayerPanelProfile> overrides;

        public Entry(String id, String name, List<Integer> slots, PlayerPanelProfile global,
            Map<String, PlayerPanelProfile> overrides) {
            if (!id.matches("main|[a-f0-9-]{36}") || name == null
                || name.trim()
                    .isEmpty()
                || name.length() > 32
                || name.chars()
                    .anyMatch(c -> c < 32 || c == 127 || c == 167))
                throw new IllegalArgumentException("Invalid panel name/ID");
            Set<Integer> seen = new HashSet<>();
            for (int slot : slots) if (slot < 0 || slot > 4095 || !seen.add(slot))
                throw new IllegalArgumentException("Invalid slot membership");
            if (MAIN.equals(id) && !slots.isEmpty()) throw new IllegalArgumentException("Main uses unreserved slots");
            this.id = id;
            this.name = name.trim();
            this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
            this.global = global;
            this.overrides = Collections.unmodifiableMap(new TreeMap<>(overrides));
        }

        public Entry withSlots(List<Integer> slots) {
            return new Entry(id, name, slots, global, overrides);
        }

        public Entry named(String name) {
            return new Entry(id, name, slots, global, overrides);
        }

        public PlayerPanelProfile resolve(String key) {
            return overrides.containsKey(key) ? overrides.get(key) : global;
        }

        public Entry profile(String key, PlayerPanelProfile profile) {
            Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
            if (key == null) return new Entry(id, name, slots, profile, next);
            if (profile == null) next.remove(key);
            else next.put(key, profile);
            return new Entry(id, name, slots, global, next);
        }

        public Entry reset(String group) {
            Map<String, PlayerPanelProfile> next = new TreeMap<>(overrides);
            next.keySet()
                .removeIf(key -> group == null || group.equals(PlayerPanelProfiles.group(key.split("\\|")[1])));
            return new Entry(id, name, slots, global, next);
        }
    }
}
