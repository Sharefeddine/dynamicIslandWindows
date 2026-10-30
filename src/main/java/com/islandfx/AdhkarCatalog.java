package com.islandfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Arabic texts and their supplied counts/sources, kept offline in the application. */
final class AdhkarCatalog {
    enum Period { MORNING, EVENING }
    record Entry(int order, String text, int count, String countDescription, String source, int type) { }
    private final List<Entry> entries;

    AdhkarCatalog(List<Entry> entries) { this.entries = List.copyOf(entries); }

    static AdhkarCatalog load() throws IOException {
        try (InputStream input = AdhkarCatalog.class.getResourceAsStream("data/adhkar-ar.json")) {
            if (input == null) throw new IOException("Arabic adhkar data is missing");
            JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) throw new IOException("Arabic adhkar data is invalid");
            List<Entry> entries = new ArrayList<>();
            JsonArray array = root.getAsJsonArray();
            for (JsonElement item : array) {
                JsonObject object = item.getAsJsonObject();
                Entry entry = new Entry(object.get("order").getAsInt(), string(object, "content"),
                        object.get("count").getAsInt(), string(object, "count_description"),
                        string(object, "source"), object.get("type").getAsInt());
                if (entry.text().isBlank() || entry.count() < 1 || entry.type() < 0 || entry.type() > 2) {
                    throw new IOException("Arabic adhkar data contains an invalid entry");
                }
                entries.add(entry);
            }
            entries.sort(Comparator.comparingInt(Entry::order));
            if (entries.isEmpty()) throw new IOException("Arabic adhkar data is empty");
            return new AdhkarCatalog(entries);
        } catch (RuntimeException invalid) {
            throw new IOException("Arabic adhkar data could not be read", invalid);
        }
    }

    List<Entry> forPeriod(Period period) {
        int type = period == Period.MORNING ? 1 : 2;
        return entries.stream().filter(entry -> entry.type() == 0 || entry.type() == type).toList();
    }
    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
