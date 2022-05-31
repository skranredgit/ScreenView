
package com.example.test5.async.http;

import android.text.TextUtils;

public class BasicNameValuePair implements NameValuePair, Cloneable {


    private final String name;
    private final String value;

    public BasicNameValuePair(final String name, final String value) {
        super();
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return this.name;
    }

    public String getValue() {
        return this.value;
    }

    public String toString() {
        return name + "=" + value;
    }

    public boolean equals(final Object object) {
        if (object == null) return false;
        if (this == object) return true;
        if (object instanceof NameValuePair) {
            BasicNameValuePair that = (BasicNameValuePair) object;
            return this.name.equals(that.name)
                  && TextUtils.equals(this.value, that.value);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return name.hashCode() ^ value.hashCode();
    }
    
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
 
}
