
### Android Plugin and Library to automate taking screenshots on Android

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat)](https://github.com/blablacar/android-screenshots/blob/master/LICENSE.txt)

###### Android Plugin and Library to automate taking screenshots on Android with custom content on multiple devices and with multiple languages

Usage
----

- For the plugin :
  - For all gradle versions : 
  ```gradle
  buildscript {
    repositories {
      maven {
        url "https://plugins.gradle.org/m2/"
      }
    }
    dependencies {
      classpath "gradle.plugin.com.comuto.screenshots:screenshots-plugin:0.1.7"
    }
  }
  
  apply plugin: "com.comuto.screenshots"
  ```
  
  - For Gradle >= 2.1 
  ```gradle
    plugins {
      id "com.comuto.screenshots" version "0.1.7"
    }
    ```
    
Configuration
=============

Example: 
```
screenshots {
    phone="192.168.58.101:5555              "
    sevenInchDevice=""
    tenInchDevice=""
    buildType = "debug"
    productFlavor = "screenshots"
    appPackageName = "com.comuto.screenshots"
    screenshotClass = "com.comuto.screenshots.TakeScreenshot"
    screenshotsDir = "screenshots"
    finalOutputDir = "framedScreenshots"
    configFilePath = "screenshots-config.properties"
    configFolder = "config"
    translationsFolder = "config/translations"
    //customJsonValuesFolder = "config/custom"
    hasApkSplit = false
    dataPlaceholdersFiles = ["placeholder_screenshots.json"]
}
```

```
phone :                     id of the phone (run adb devices to get it) to take screenshots from.
sevenInchDevice :           id of a seven inch device to take screenshots. 
tenInchDevice :             id of a ten inch device to take screenshots.
buildType :                 buildType of the flavor to run.
productFlavor :             flavor to run.
appPackageName :            the application id of the app 
screenshotClass :           Espresso test class name 
screenshotsDir :            directory in which to save the generated screenshots 
finalOutputDir :            final directory in which to save the screenshots after all steps are done (framing, ...)
configFilePath :            file containg locales configuration 
configFolder :              folder that contains the json to be generated configuration and translated strings 
translationsFolder :        folder containing the translations of values to put in json 
customJsonValuesFolder :    custom folder for the json values that are in locale (not fetched remotely)
hasApkSplit :               whether or not the app apks are using apk split 
dataPlaceholdersFiles :     collection of the template of jsons to generate 
```    
    
How does it work ? 
================== 
This plugin orchestrates the following actions:
 - Uses [json generator](https://github.com/chemouna/JsonGenerator) to generate and create custom content in json files for okhttp mocks  
 - Uses Espresso to create tests that navigate to screens from which to capture screenshots 
    and uses [capture lib](https://github.com/chemouna/capture) to take a screenshot.
 - run these tests repeatedly for each locale provided in the configuration file and takes 
    screenshots for each one.
 - then pulls the images from the device.
 - then uses [frame screenshots plugin](https://github.com/chemouna/frame-gradle-plugin) to frame the screenshots with the provided titles for 
    each locale (if it in the dependencies and is configured). 


Contributing
============

* [Check for open issues](https://github.com/blablacar/android-screenshots/issues) or open
   a fresh issue to start a discussion around a feature idea or a bug.
* Fork the [repository on Github](https://github.com/blablacar/android-screenshots)
   to start making your changes.
* Send a pull request and bug the maintainer until it gets merged and published.
   :) Make sure to add yourself to ``CONTRIBUTORS.txt``.

License
-------

    Copyright (C) 2016 BlablaCar

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.



