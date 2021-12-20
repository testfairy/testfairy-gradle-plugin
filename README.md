TestFairy Gradle Plugin [![Build Status](https://travis-ci.org/testfairy/testfairy-gradle-plugin.svg?branch=master)](https://travis-ci.org/testfairy/testfairy-gradle-plugin) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
-------------------

This plugin integrates TestFairy platform with the Gradle build system. With this plugin, you can upload signed builds directly via command line, IntelliJ, Android Studio and other IDEs.

Installation
---------

A typical TestFairy Gradle Plugin installation takes less than 1 minute. 

Installation consists of adding the following lines to your ***build.gradle*** files:

```
// Project's build.gradle
buildscript {
    repositories {
        maven { url 'https://www.testfairy.com/maven' }
    }
    dependencies {
        classpath 'com.testfairy.plugins.gradle:testfairy:3.+'
    }
}

// App module's build.gradle
apply plugin: 'testfairy'
testfairyConfig {
    apiKey "1234567890abcdef"
    uploadProguardMapping true    
}
```

**NOTE:** Your TestFairy API key is in your [account settings](https://app.testfairy.com/settings#apikey)
     
Usage
-----

With the plugin installed, a set of new tasks, prefixed "*testfairy*" will be added, one for each build type.

For example: to upload a debug build, run the following from terminal:

    gradlew testfairyDebug
    
Optionally, you can add a *changelog* to this build. This changelog will appear in your build notes and as a default message when inviting testers. For example:

    gradlew -PtestfairyChangelog="Fixed all bugs" testfairyDebug

Similarly, you can also tag your releases like below. Tags given like this are merged with others from the plugin configuration.

    gradlew -PtestfairyTags="feature1,feature2,alpha" testfairyDebug
    
Uploading Crash Symbols
-----------------------

Android projects using native libraries are likely to turn on compiler flags which strip symbol names from the final binaries. In release builds, these configurations result in crash reports with illegible stack trace lines. In order to reveal the symbols in a stripped build's crash report, you must upload a collection of your symbols to TestFairy.

With the plugin installed, a set of new tasks, prefixed "*testfairyNdk*" will be added, one for each build type.

* To upload native symbols, run: `./gradlew testfairyNdkDebug` in your project root.
    
Additional Parameters
---------------------

This Gradle plugin uploads APK artifacts to TestFairy for distribution. We strongly suggest you also integrate the TestFairy SDK. Additional parameters control the default behavior of newly uploaded builds:

| Property           | Description |
|--------------------|-------------|
| apiKey             | API key used for uploading |
| video              | Should record video? Values: on/off/wifi (default: on) |
| videoQuality       | Image quality of video. Values: high/medium/low (default: medium) |
| testersGroups      | Comma seperated list of testers-groups to invite |
| notify             | Should send emails to these testers? Values: true/false (default: true) |
| autoUpdate         | Display and enable auto update for testers using older versions? Values: true/false (default: false) |
| recordOnBackground | Should record metrics even if app is in background (Android only)? Values: true/false (default: false) |
| tags               | Comma separated list of tag strings which will be attached to current build |

Using a Web Proxy
--------------------------------

Behind a firewall at work? TestFairy Gradle Plugin supports HTTP proxy via "*http.proxyHost*" system property. Please refer to the [Accessing The Web Via a Proxy](http://www.gradle.org/docs/current/userguide/build_environment.html#sec:accessing_the_web_via_a_proxy) section in the Gradle user guide document.

Android Studio / IntelliJ
-------------------------

This plugin is also Android Studio and Intellij-friendly. To upload builds directly from your IDE:

1. Open "Edit Configuration..." dialog

 ![Edit Configuration screenshot][1] 

2. Add a new Gradle configuration, use task "*testfairyDebug*" or another, depending on your build type.

 ![Add new Gradle configuration screenshot][2]

<a name="migrate_2x"></a>
Migrating from 2.x to 3.x
----

Version 3.0 deprecated the support for gradle plugins older than 3.4 and Android Studio build tools older than 5.1.1.

To migrate, simply upgrade those dependencies in your module's build.gradle and gradle-wrapper.properties files.


<a name="migrate_1x"></a>
Migrating from 1.x to 2.x
----

Version 2.0 deprecated the support for instrumentation. Please use the [TestFairy SDK](https://docs.testfairy.com/Android/Integrating_Android_SDK.html) to record sessions, auto-update versions, and handle crashes. 

To migrate, simply re-integrate the plugin into your module's build.gradle file.

- *iconWatermark* has been removed

Changelog
----
3.6 (2021-12-20)
  - Updated core frameworks.

3.5 (2020-01-20)
  - Added more heuristics to find output APK file. 
  
3.4 (2020-01-16)
  - Added `-PtestfairyTags` CLI parameter.
  - Removed deprecated Gradle API usage. 
  
3.3 (2019-12-31)
  - Added `tags` and `custom` plugin parameters.
  
3.2 (2019-12-05)
  - Added a new task to upload NDK symbols to TestFairy.

3.0 (2019-05-20)
  - Added support for latest Gradle and Android Plugin (3.4+ and 5.1.1+ respectively)
  
2.0 (2017-09-01)
  - Added support for latest Gradle and Android Plugin
  - Added support for Android Studio 3
  - Removed support for instrumentation
  - Removed dependency for zipaplign and jarsigner
  - Removed iconWatermark
  
1.12 (2015-02-04)
  - Removed dependency for 'zip' command.
  - Zipalign signed APK before uploading.
  - Compatible with JDK 1.6.

Development
----

* Install IntelliJ IDEA.
* Install Groovy SDK if IntelliJ fails importing groovy dependencies. (only affects static analysis)
* Open project and setup latest Groovy SDK and Java SDK (1.8) path.
* Sync.

To test the plugin after code change, run:

```bash
export TF_API_KEY=${{ secrets.TF_API_KEY }}

./gradlew uploadArchives
cd example/TestApplication
./gradlew testfairyDebug
```

Bugs
----

Please send bug reports to support@testfairy.com 

[1]: https://raw.githubusercontent.com/testfairy/testfairy-gradle-plugin/master/docs/images/preview-open-edit-configurations.png
[2]: https://raw.githubusercontent.com/testfairy/testfairy-gradle-plugin/master/docs/images/preview-add-gradle-task.png

