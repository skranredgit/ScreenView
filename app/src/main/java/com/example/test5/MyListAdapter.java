package com.example.test5;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class MyListAdapter extends BaseAdapter {
    private LayoutInflater LInflater;
    private ArrayList<Dataip> list;
    public MyListAdapter(Context context, ArrayList<Dataip> data){

        list = data;
        LInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Dataip getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;
        View v = convertView;
        if ( v == null){
            holder = new ViewHolder();
            v = LInflater.inflate(R.layout.listview, parent, false);
            holder.ip = (TextView) v.findViewById(R.id.ip);
            holder.mack = ((TextView) v.findViewById(R.id.mack));
            v.setTag(holder);
        }
        holder = (ViewHolder) v.getTag();
        Dataip dataFlags = getData(position);

        holder.ip.setText("ip: " + dataFlags.getip());
        holder.mack.setText("mack: " + dataFlags.getmack());

        return v;
    }

    Dataip getData(int position){
        return (getItem(position));
    }

    private static class ViewHolder {
        private TextView ip;
        private TextView mack;
    }
}