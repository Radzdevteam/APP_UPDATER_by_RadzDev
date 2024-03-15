# Radz App Updater

Welcome to Radz App Updater - Deprecated JSON, developed by Radz.

## Overview

Radz App Updater is a tool designed to simplify the update process for your applications, using a deprecated JSON format. This README file provides detailed instructions for setup and usage.

## Installation

To integrate Radz App Updater into your project, follow these steps:

1. **Dependencies**: Add the following dependency to your project's build.gradle file:

    ```gradle
    implementation("com.airbnb.android:lottie:4.0.0")
    ```

2. **File Configuration**: Create a `file_paths.xml` file in the `xml` directory of your project with the following content:

    ```xml
    <paths xmlns:android="http://schemas.android.com/apk/res/android">
        <external-path name="external_files" path="." />
    </paths>
    ```

3. **Permissions**: Add the following permissions to your AndroidManifest.xml file:

    ```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    ```

4. **Constant Configuration**: Define your web update link in your Constants file:

    ```java
    public static final String APP_UPDATER = "https://radzvpn.com/raw?id=119";
    ```

## JSON Format

To utilize Radz App Updater, follow the JSON format for your update information:

```json
{
  "versionCode": 2,
  "updateLink": "https://radzpro.com/test.apk",
  "size": "4.7 MB",
  "updateContent": "To use this app, download the latest version"
}
