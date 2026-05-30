package com.github.paul035;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

// https://www.geeksforgeeks.org/how-to-change-the-whole-app-language-in-android-programmatically/
public class LocaleHelper {

    // Method to set the language at runtime
    public static Context setLocale(Context context, String language) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? updateResources(context, language) : updateResourcesLegacy(context, language);
    }

    // Method to update the language of the application by creating
    // an object of the inbuilt Locale class and passing the language argument to it
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private static Context updateResources(Context context, String language) {
        String[] codes = language.split("-");
        Locale locale = codes.length > 1 ? new Locale(codes[0], codes[1]) : new Locale(language);
        Locale.setDefault(locale);

        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);

        return context.createConfigurationContext(configuration);
    }

    private static Context updateResourcesLegacy(Context context, String language) {
        String[] codes = language.split("-");
        Locale locale = codes.length > 1 ? new Locale(codes[0], codes[1]) : new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;

        configuration.setLayoutDirection(locale);

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        return context;
    }
}