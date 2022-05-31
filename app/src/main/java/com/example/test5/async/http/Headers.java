package com.example.test5.async.http;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class Headers {
    public Headers() {
    }

    public Headers(Map<String, List<String>> mm) {
        map.putAll(mm);
    }

    final Multimap map = new Multimap() {
        @Override
        protected List<String> newList() {
            return new TaggedList<String>();
        }
    };

    public String get(String header) {
        return map.getString(header.toLowerCase());
    }

    public Headers set(String header, String value) {
        if (value != null && (value.contains("\n") || value.contains("\r")))
            throw new IllegalArgumentException("value must not contain a new line or line feed");
        String lc = header.toLowerCase();
        map.put(lc, value);
        TaggedList<String> list = (TaggedList<String>)map.get(lc);
        list.tagNull(header);
        return this;
    }

    public Headers add(String header, String value) {
        String lc = header.toLowerCase();
        map.add(lc, value);
        TaggedList<String> list = (TaggedList<String>)map.get(lc);
        list.tagNull(header);
        return this;
    }

    public Headers addLine(String line) {
        if (line != null) {
            line = line.trim();
            String[] parts = line.split(":", 2);
            if (parts.length == 2)
                add(parts[0].trim(), parts[1].trim());
            else
                add(parts[0].trim(), "");
        }
        return this;
    }

    public Headers addAll(Headers headers) {
        map.putAll(headers.map);
        return this;
    }

    public List<String> removeAll(String header) {
        return map.remove(header.toLowerCase());
    }

    public String remove(String header) {
        List<String> r = removeAll(header.toLowerCase());
        if (r == null || r.size() == 0)
            return null;
        return r.get(0);
    }

    public StringBuilder toStringBuilder() {
        StringBuilder result = new StringBuilder(256);
        for (String key: map.keySet()) {
            TaggedList<String> list = (TaggedList<String>)map.get(key);
            for (String v: list) {
                result.append((String)list.tag())
                .append(": ")
                .append(v)
                .append("\r\n");
            }
        }
        result.append("\r\n");
        return result;
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    public String toPrefixString(String prefix) {
        return
        toStringBuilder()
        .insert(0, prefix + "\r\n")
        .toString();
    }

    public class TaggedList<T> extends ArrayList<T> {
        private Object tag;

        public synchronized <V> V tag() {
            return (V)tag;
        }

        public synchronized <V> void tag(V tag) {
            this.tag = tag;
        }

        public synchronized <V> void tagNull(V tag) {
            if (this.tag == null)
                this.tag = tag;
        }
    }
}
