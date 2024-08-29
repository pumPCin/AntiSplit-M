package com.abdurazaaqmohammed.AntiSplit.main;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import com.abdurazaaqmohammed.AntiSplit.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** @noinspection NullableProblems*/
public class CustomArrayAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final String[] values;
    private final int textColor;
    private final boolean lang;
    private String langCode;
    private final List<String> filteredList;
    private CustomFilter filter;

    public CustomArrayAdapter(Context context, String[] values, int textColor, boolean lang) {
        super(context, android.R.layout.simple_list_item_multiple_choice, values);
        this.context = context;
        this.values = values;
        this.textColor = textColor;
        this.lang = lang;
        this.filteredList = new ArrayList<>();
        if (lang) {
            String[] langCodes = MainActivity.rss.getStringArray(R.array.langs);
            for (int i = 0; i < langCodes.length; i++) {
                if (Objects.equals(MainActivity.lang, langCodes[i])) {
                    this.langCode = MainActivity.rss.getStringArray(R.array.langs_display)[i];
                    break;
                }
            }
        }
        this.filteredList.addAll(List.of(values)); // Initialize filteredList with original values
    }

    @Override
    public int getCount() {
        return filteredList.size();
    }

    @Override
    public String getItem(int position) {
        return filteredList.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(lang ? android.R.layout.simple_list_item_1 : android.R.layout.simple_list_item_multiple_choice, parent, false);
        }

        TextView textView = convertView.findViewById(android.R.id.text1);
        String curr = getItem(position);  // Use the filtered list item
        textView.setText(lang && Objects.equals(curr, langCode) ? Html.fromHtml("<b>" + curr + "</b>") : curr);
        textView.setTextColor(textColor);

        return convertView;
    }

    @Override
    public Filter getFilter() {
        if (filter == null) {
            filter = new CustomFilter();
        }
        return filter;
    }

    private class CustomFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            if (constraint == null || constraint.length() == 0) {
                results.values = List.of(values);
                results.count = values.length;
            } else {
                List<String> filteredItems = new ArrayList<>();
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (String item : values) {
                    if (item.toLowerCase().contains(filterPattern)) {
                        filteredItems.add(item);
                    }
                }

                results.values = filteredItems;
                results.count = filteredItems.size();
            }
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredList.clear();
            filteredList.addAll((List<String>) results.values);
            notifyDataSetChanged();
        }
    }
}
