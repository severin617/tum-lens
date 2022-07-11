package com.google.mediapipe.apps.sign_translator;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.BaseAdapter;

public class SettingsActivity extends AppCompatActivity{
    private static final String TAG = "SettingsActivity";
    private final String [] titles = {"Email", "Version"};
    private final String [] descriptions = {"maximilian.karpfinger@tum.de", "1.0"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutResId());
        final ListView listview = (ListView) findViewById(R.id.list_view);
        final MyAdapter adapter = new MyAdapter();
        listview.setAdapter(adapter);
    }

    protected int getContentViewLayoutResId() {
        return R.layout.activity_settings;
    }

    private class MyAdapter extends BaseAdapter {

        LayoutInflater inflater;

        public MyAdapter(){
            inflater = (LayoutInflater.from(SettingsActivity.this));
        }

        @Override
        public View getView(int position, View view, ViewGroup viewGroup) {
            view = inflater.inflate(R.layout.list_item, null);
            TextView title = (TextView) view.findViewById(R.id.item_title);
            TextView description = (TextView) view.findViewById(R.id.item_description);
            title.setText(titles[position]);
            description.setText(descriptions[position]);
            return view;
        }

        @Override
        public int getCount(){
            return titles.length;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

    }
}
