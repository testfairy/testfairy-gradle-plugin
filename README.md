TestFairy Gradle Plugin
-------------------

This plugin integrates TestFairy platform with the Gradle build system. With this plugin, you can upload signed builds directly via command line, IntelliJ, Android Studio and other IDEs.

[![Build Status](https://travis-ci.org/testfairy/testfairy-gradle-plugin.svg?branch=master)](https://travis-ci.org/testfairy/testfairy-gradle-plugin)

Quick Start
----------------
For convenience, here is a snippet of a complete ***build.gradle*** file, including the additions above.

    buildscript {
        repositories {
            mavenCentral()
        }
    
        dependencies {
            ...
            classpath 'com.testfairy:testfairy-gradle-plugin:<latest>'
        }
    }
    
    apply plugin: 'android'
    apply plugin: 'testfairy'
    
    android {
        ...
        testfairyConfig {
            apiKey "1234567890abcdef"
        }
        ...
    }

Usage
-----

With the plugin installed, a set of new tasks, prefixed "*testfairy*" will be added, one for each build type.

For example: to upload a debug build, run the following from terminal:

    gradlew testfairyDebug
    
Optionally, you can add a *changelog* to this build. This changelog will appear in your build notes and as a default message when inviting testers. For example:

    gradlew -PtestfairyChangelog="Fixed all bugs" testfairyDebug
    
Additional Parameters
---------------------

Values in `testfairyConfig` can be also be passed at the command line prefixed with `-P`. 

| testfairyConfig       | command line option            | value                                                                                 |
| ---------------       | -------------------            | -----                                                                                 |
| apiKey                | testfairyApiKey                | key from http://app.testfairy.com/settings                                            |
| iconWatermark         | testfairyWatermark             | true/false                                                                            |
| video                 | testfairyVideo                 | "on"/"off"/"wifi"                                                                     |
| videoQuality          | testfairyVideoQuality          | "high"/"medium"/"low"                                                                 |
| videoRate             | testfairyVideoRate             | float as string eg "1.0"                                                              |
| testersGroups         | testfairyTesterGroups          | comma separated list or "all" eg. "qa,dev"                                            |
| maxDuration           | testfairyMaxDuration           | duration eg "30m" or "1h"                                                             |
| metrics               | testfairyMetrics               | comma separated list eg. "cpu,memory,logcat"                                          |
| comment               | testfairyComment               | comment or path to text file (prefix with @) eg. "comment" or "@/path/to/comment.txt" |
| changelog             | testfairyChangelog             | changelog string                                                                      |
| digestalg             | testfairyDigestalg             | Digest Algorithm defaults to "SHA1"                                                   |
| sigalg                | testfairySigalg                | Signature Algorithm defaults to "MD5withRSA"                                          |
| notify                | testfairyNotify                | true/false                                                                            |
| anonymous             | testfairyAnonymous             | true/false                                                                            |
| shake                 | testfairyShake                 | true/false                                                                            |
| autoUpdate            | testfairyAutoUpdate            | true/false                                                                            |
| recordOnBackground    | testfairyRecordOnBackground    | true/false                                                                            |
| uploadProguardMapping | testfairyUploadProguardMapping | true/false                                                                            |

So the follow two are equivalent (note that uploadProguardMapping:
```
android {
    testfairyConfig {
        apiKey "api_key"
        iconWatermark true
        metrics "cpu,memory,network,logcat"
        video "wifi"
        videoRate "0.5"
        videoQuality "low"
        maxDuration "15m"
        recordOnBackground true
        testersGroups "dev,qa,friends"
        notify true
        autoUpdate true
        uploadProguardMapping true
    }
}
```

```
> ./gradle testfairyDebug -PtestfairyApiKey=api_key -PtestfairyWatermark=true -PtestfairyMetrics=cpu,memory,network,logcat -PtestfairyVideo=wifi -PtestfairyVideoRate=0.5 -PtestfairyMaxDuration=15m -PtestfairyRecordOnBackground=true -PtestfairyTesterGroups=dev,qa,friends -PtestfairyNotify=true -PtestfairyAutoUpdate=true -PtestfairyUploadProguardMapping=true
```
This allows you define variables at the command line or in a `gradle.properties` file. Arguments at the command line preceed the values in `testfairyConfig`. Arguments such as testfairyApiKey can even accept environment variables securely from build systems.

For more details about parameter values, see [this](http://docs.testfairy.com/Upload_API.html).

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

Changelog
----

1.12 (2015-02-04)
  - Removed dependency for 'zip' command.
  - Zipalign signed APK before uploading.
  - Compatible with JDK 1.6.

Bugs
----

Please send bug reports to support@testfairy.com 

[1]: https://raw.githubusercontent.com/testfairy/testfairy-gradle-plugin/master/docs/images/preview-open-edit-configurations.png
[2]: https://raw.githubusercontent.com/testfairy/testfairy-gradle-plugin/master/docs/images/preview-add-gradle-task.png
            
