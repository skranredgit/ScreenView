package com.example.test5.async.http;

import android.net.Uri;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

public class Multimap extends LinkedHashMap<String, List<String>> implements Iterable<NameValuePair> {
    public Multimap() {
    }

    protected List<String> newList() {
        return new ArrayList<String>();
    }

    public String getString(String name) {
        List<String> ret = get(name);
        if (ret == null || ret.size() == 0)
            return null;
        return ret.get(0);
    }

    public void add(String name, String value) {
        List<String> ret = get(name);
        if (ret == null) {
            ret = newList();
            put(name, ret);
        }
        ret.add(value);
    }

    public void put(String name, String value) {
        List<String> ret = newList();
        ret.add(value);
        put(name, ret);
    }


    public interface StringDecoder {
        public String decode(String s);
    }

    public static Multimap parse(String value, String delimiter, boolean unquote, StringDecoder decoder) {
        Multimap map = new Multimap();
        if (value == null)
            return map;
        String[] parts = value.split(delimiter);
        for (String part: parts) {
            String[] pair = part.split("=", 2);
            String key = pair[0].trim();
            String v = null;
            if (pair.length > 1)
                v = pair[1];
            if (unquote && v != null && v.endsWith("\"") && v.startsWith("\""))
                v = v.substring(1, v.length() - 1);
            if (decoder != null) {
                key = decoder.decode(key);
                v = decoder.decode(v);
            }
            map.add(key, v);
        }
        return map;
    }


    private static final StringDecoder QUERY_DECODER = new StringDecoder() {
        @Override
        public String decode(String s) {
            return Uri.decode(s);
        }
    };

    public static Multimap parseQuery(String query) {
        return parse(query, "&", false, QUERY_DECODER);
    }

    private static final StringDecoder URL_DECODER = new StringDecoder() {
        @Override
        public String decode(String s) {
            return URLDecoder.decode(s);
        }
    };

    @Override
    public Iterator<NameValuePair> iterator() {
        ArrayList<NameValuePair> ret = new ArrayList<NameValuePair>();
        for (String name: keySet()) {
            List<String> values = get(name);
            for (String value: values) {
                ret.add(new BasicNameValuePair(name, value));
            }
        }
        return ret.iterator();
    }
}
