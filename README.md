TestFairy Gradle Plugin
-------------------

This plugin integrates TestFairy platform with the Gradle build system. With this plugin, you can upload signed builds directly via command line, IntelliJ, Android Studio and other IDEs.

Installation
---------

A typical TestFairy Gradle Plugin installation takes less than 20 seconds. Installation consists of adding the following to your ***build.gradle*** file:

 1. Add the TestFairy Maven repository:

        maven { url 'https://www.testfairy.com/maven' }
    
 2. Add plugin dependency: 

        classpath 'com.testfairy.plugins.gradle:testfairy:1.+'

 3. Apply plugin:

        apply plugin: 'testfairy'

 4. Configure API key by adding this to your "*android*" section:

        testfairyConfig {
            apiKey "1234567890abcdef"
        }

Complete Example
----------------

For convenience, here is a snippet of a complete ***build.gradle*** file, including the additions above.

    buildscript {
        repositories {
            mavenCentral()
            maven { url 'https://www.testfairy.com/maven' }
        }
    
        dependencies {
            classpath 'com.android.tools.build:gradle:0.6'
            classpath 'com.testfairy.plugins.gradle:testfairy:1.+'
        }
    }
    
    apply plugin: 'android'
    apply plugin: 'testfairy'
    
    android {
        testfairyConfig {
            apiKey "1234567890abcdef"
        }
    }


Usage
-----

With the plugin installed, a set of new tasks, prefixed "*testfairy*" will be added, one for each build type.

For example: to upload a debug build, run the following from terminal:

    gradlew testfairyDebug
    
Optionally, you can add a *changelog* to this build. This changelog will appear in your build notes and as a default message when inviting testers. For example:

    gradlew -PtestfairyChangelog="Fixed all bugs" testfairyDebug

Android Studio / IntelliJ
-------------------------

This plugin is also Android Studio and Intellij-friendly. To upload builds directly from your IDE:

1. Open "Edit Configuration..." dialog

 ![Edit Configuration screenshot][1] 

2. Add a new Gradle configuration, use task "*testfairyDebug*" or another, depending on your build type.

 ![Add new Gradle configuration screenshot][2]

Bugs
----

Please send bug reports to support@testfairy.com or use GitHub to open issues at:

 https://github.com/testfairy/testfairy-gradle-plugin/issues


[1]: https://raw2.github.com/testfairy/testfairy-gradle-plugin/master/docs/images/preview-open-edit-configurations.png
[2]: https://raw2.github.com/testfairy/testfairy-gradle-plugin/master/docs/images/preview-add-gradle-task.png
            
