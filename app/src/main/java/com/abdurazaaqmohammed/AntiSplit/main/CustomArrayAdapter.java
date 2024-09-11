package com.abdurazaaqmohammed.AntiSplit.main;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.abdurazaaqmohammed.AntiSplit.R;

import java.util.Objects;

/** @noinspection NullableProblems*/
public class CustomArrayAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final String[] values;
    private final boolean lang;
    private String langCode;

    public CustomArrayAdapter(Context context, String[] values, boolean lang) {
        super(context, android.R.layout.simple_list_item_multiple_choice, values);
        this.context = context;
        this.values = values;
        this.lang = lang;
        if (lang) {
            String[] langCodes = MainActivity.rss.getStringArray(R.array.langs);
            for (int i = 0; i < langCodes.length; i++) {
                if (Objects.equals(MainActivity.lang, langCodes[i])) {
                    this.langCode = MainActivity.rss.getStringArray(R.array.langs_display)[i];
                    break;
                }
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(lang ? android.R.layout.simple_list_item_1 : android.R.layout.simple_list_item_multiple_choice, parent, false);
        }
        TextView textView = convertView.findViewById(android.R.id.text1);
        String curr = values[position];
        textView.setText(lang && Objects.equals(curr, langCode) ? Html.fromHtml(("<b>" + curr + "</b>")) : curr);
        return convertView;
    }
}