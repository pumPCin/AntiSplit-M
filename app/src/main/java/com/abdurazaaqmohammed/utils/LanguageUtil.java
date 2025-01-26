package com.abdurazaaqmohammed.utils;

import android.content.res.Resources;
import android.widget.ScrollView;
import android.widget.TextView;

import com.abdurazaaqmohammed.AntiSplit.R;
import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;
import com.google.android.material.textfield.TextInputLayout;

public class LanguageUtil {
    public static void updateLang(Resources res, ScrollView settingsDialog, MainActivity context) {
        updateMain(res, context);
        updateSettingsDialog(settingsDialog, res);
    }

    public static void updateMain(Resources res, MainActivity context) {
        context.setRss(res);
        context.<TextView>findViewById(R.id.decodeButton).setText(res.getString(R.string.merge));
        context.<TextView>findViewById(R.id.fromAppsButton).setText(res.getString(R.string.select_from_installed_apps));
        context.findViewById(R.id.settingsButton).setContentDescription(res.getString(R.string.settings));
        context.findViewById(R.id.installButton).setContentDescription(res.getString(R.string.install));
        context.findViewById(R.id.cancelButton).setContentDescription(res.getString(R.string.cancel));
        context.findViewById(R.id.copyButton).setContentDescription(res.getString(R.string.copy_log));
    }

    public static void updateSettingsDialog(ScrollView settingsDialog, Resources res) {
        ((TextView) settingsDialog.findViewById(R.id.langPicker)).setText(res.getString(R.string.lang));
        ((TextView) settingsDialog.findViewById(R.id.logToggle)).setText(res.getString(R.string.enable_logs));
        ((TextView) settingsDialog.findViewById(R.id.ask)).setText(res.getString(R.string.ask));
        ((TextView) settingsDialog.findViewById(R.id.showDialogToggle)).setText(res.getString(R.string.show_dialog));
        ((TextView) settingsDialog.findViewById(R.id.signToggle)).setText(res.getString(R.string.sign_apk));
        ((TextView) settingsDialog.findViewById(R.id.forceToggle)).setText(res.getString(R.string.force));
        ((TextView) settingsDialog.findViewById(R.id.selectSplitsForDeviceToggle)).setText(res.getString(R.string.automatically_select));
        ((TextView) settingsDialog.findViewById(R.id.updateToggle)).setText(res.getString(R.string.auto_update));
        ((TextView) settingsDialog.findViewById(R.id.checkUpdateNow)).setText(res.getString(R.string.check_update_now));
        ((TextInputLayout) settingsDialog.findViewById(R.id.suffixLayout)).setHint(res.getString(R.string.suffix));
    }
}
