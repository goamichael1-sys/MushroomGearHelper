# Mushroom Gear Helper (Android APK)

Native Android port of the Mushroom Gear Helper Chrome Extension for **Legend of Mushroom**.

Automatically reads gear comparison popups via screen capture and taps **Sell** or **Equip** using Android's Accessibility service without requiring root.

---

## 🚀 How to Build the APK (Easiest Way - In the Cloud)

You do **not** need Android Studio or any heavy software installed on your computer.

1. Go to [github.com](https://github.com) and create a free account (if you don't have one).
2. Create a new repository named `MushroomGearHelper`.
3. Upload all the files from this folder (`C:\Users\Xidad\.gemini\antigravity\scratch\MushroomGearHelperAndroid`) into your repository.
4. Click on the **Actions** tab in GitHub.
5. You will see the **Build Mushroom Gear Helper APK** workflow running automatically!
6. Once it turns green (~1-2 minutes), click on the build run, scroll to the bottom under **Artifacts**, and download `MushroomGearHelper-debug-apk`.
7. Unzip the downloaded file and install `app-debug.apk` on your phone!

---

## 🛠️ Alternative: Build with Android Studio (Local PC)

1. Download and install [Android Studio](https://developer.android.com/studio).
2. Select **Open** and choose the `MushroomGearHelperAndroid` folder.
3. Wait for Gradle to finish syncing.
4. In the top menu, go to **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**.
5. Android Studio will show a popup when finished with a **locate** link to your `app-debug.apk`.

---

## 📱 How to Use on Your Phone

1. Install and open **Mushroom Gear Helper**.
2. **Step 1:** Tap **Grant Overlay Permission** → enable "Display over other apps" for Mushroom Gear Helper.
3. **Step 2:** Tap **Enable Accessibility Service** → find **Mushroom Gear Helper** in the list and toggle it **ON**.
4. Leave **Dry Run** ON at first if you want to test what it would do before letting it click.
5. Tap the green **Start Mushroom Helper** button and allow the screen capture prompt.
6. Open **Legend of Mushroom**.
7. You will see a small floating bubble (`🍄 Helper Ready ▶ ✕`) over your game:
   - Tap **▶** to start automatic comparison and clicking.
   - Tap **⏸** to pause anytime.
   - Drag the bubble anywhere on your screen.
