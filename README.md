# AntiSplit M
Android app to merge/"AntiSplit" split APKs (APKS/XAPK/APKM) to a regular .APK file

**Note: If you are using this app to get an APK to patch in ReVanced Manager, you should enable the option "Show dialog allowing selection of which splits to include" and in the dialog select "Select for your device specifications" (see screenshots below)**

This project is a simple GUI implementation of Merge utilities from [REAndroid APKEditor](https://github.com/REAndroid/APKEditor).

There are already some apps that can perform this task like Apktool M, AntiSplit G2, NP Manager, but they are all closed source. 

In addition, Antisplit G2 (com.tilks.arscmerge), the fastest and lightest of the existing apps, has a large problem; it does not remove the information about splits in the APK from the AndroidManifest.xml. If a non-split APK contains this information it will cause an "App not installed" error on some devices. Fortunately the implementation by REAndroid contains a function to remove this automatically and it carries over to this app.

# Usage
Video - https://www.youtube.com/watch?v=Nd3vEzRWY-Q

There are 3 ways to open the split APK to be merged:
* Share the file and select AntiSplit M in the share menu
* Press (open) the file and select AntiSplit M in available options
* Open the app from launcher and press the button then select the split APK file.
   * This option does not work on Android < 4.4, use one of the 2 other options or type the path to the APK (on your device storage) into the box in the app.

Note: if you are planning to further modify the APK, you only need to sign it after the modifications.

Note: Some apps verify the signature of the APK or take other measures to check if the app was modified, which may cause it to crash on startup.

# Screenshots
<img src="/images/1.6 mainscreen.png" height="540" width="243" /> <img src="/images/1.6 dialog.png" height="540" width="243" /> <img src="/images/1.6 result.png" height="540" width="243" />

# Todo
* support picking from installed apps
