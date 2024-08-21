package com.abdurazaaqmohammed.AntiSplit.main;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class CustomArrayAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final String[] values;
    private final int textColor;
    private final boolean lang;

    public CustomArrayAdapter(Context context, String[] values, int textColor, boolean lang) {
        super(context, android.R.layout.simple_list_item_multiple_choice, values);
        this.context = context;
        this.values = values;
        this.textColor = textColor;
        this.lang = lang;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(lang ? android.R.layout.simple_list_item_1 : android.R.layout.simple_list_item_multiple_choice, parent, false);
        }

        TextView textView = convertView.findViewById(android.R.id.text1);
        textView.setText(values[position]);
        textView.setTextColor(textColor);

        return convertView;
    }
}