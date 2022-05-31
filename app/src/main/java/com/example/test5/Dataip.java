package com.example.test5;

public class Dataip {

    private String name;
    private String ip;
    private String mack;

    public Dataip (String _name, String _ip, String _mack){
        name = _name;
        ip = _ip;
        mack = _mack;
    }

    public String getname(){
        return name;
    }

    public String getip(){
        return ip;
    }

    public String getmack() {
        return mack;
    }
}