<?php
	class GradleSupportMatrixTest extends PHPUnit_Framework_TestCase
	{
		private $_apiKey = "9dc08e8d93efd8622178f0c61faeaf112fbafcb4";

		// list of Android Plugins is available at http://tools.android.com/tech-docs/new-build-system
		// list of Gradle Wrappers is available at http://services.gradle.org/distributions

		public function setup() {
			parent::setup();

			// where is our development maven repository?
			$this->_projectDir = realpath(__DIR__ . "/../..");
		}

		/**
		 * Changes the distributionUrl inside a gradle-wrapper.properties file
		 *
		 * @param $filename
		 * @param $wrapperVersion
		 */
		private function changeDistributionUrl($filename, $wrapperVersion) {
			$lines = file($filename, FILE_IGNORE_NEW_LINES);
			for ($i=0; $i<count($lines); $i++) {
				if (substr($lines[$i], 0, strlen("distributionUrl=")) == "distributionUrl=") {
					$lines[$i] = "distributionUrl=http\\://services.gradle.org/distributions/gradle-" . $wrapperVersion . "-all.zip";
				}
			}

			file_put_contents($filename, implode("\n", $lines));
		}

		/**
		 * Automagically add testfairy gradle plugin to the gradle build script.
		 *
		 * @param $filename   string     path for build.gradle file being inspected
		 * @param $keystore   string     path to keystore file used for signing app
		 */
		private function fixupBuildGradle($filename, $keystore) {
			$lines = file($filename, FILE_IGNORE_NEW_LINES);
			$out = array();

			foreach($lines as $line) {

				if (preg_match("/^\\s*runProguard false/", $line)) {
					$line = str_replace("runProguard false", "minifyEnabled true", $line);
				}

				$out[] = $line;

				if (strpos($line, "repositories {") !== FALSE) {
					//$out[] = "        maven { url 'https://www.testfairy.com/maven' }";
					$out[] = "        maven { url 'file://" . $this->_projectDir . "/repo' }";
				}

				if (strpos($line, "dependencies {") !== FALSE) {
					$out[] = "        classpath 'com.testfairy.plugins.gradle:testfairy:2.+'";
				}

				if (strpos($line, "apply plugin") !== FALSE) {
					$out[] = "apply plugin: 'testfairy'";
				}

				if (strpos($line, "android {") !== FALSE) {
					$out[] = "signingConfigs {";
        				$out[] = "  release {";
                                        $out[] = "    storeFile file(\"$keystore\")";
                                        $out[] = "    storePassword \"swordfish\"";
                                        $out[] = "    keyAlias \"android_app\"";
                                        $out[] = "    keyPassword \"swordfish\"";
                                        $out[] = "  }";
                                        $out[] = "}";

					$out[] = "buildTypes {";
					$out[] = "  release {";
					$out[] = "    signingConfig signingConfigs.release";
					//runProguard true
					//proguardFile getDefaultProguardFile('proguard-android.txt')
					$out[] = "  }";
					$out[] = "}";

					$out[] = "    testfairyConfig {";
					//$out[] = "       serverEndpoint \"http://" . $this->_tested_server . "\"";
					$out[] = "       apiKey \"" . $this->_apiKey . "\"";
					$out[] = "       uploadProguardMapping true";
					$out[] = "    }";
				}
			}

			$lines = implode("\n", $out);
			file_put_contents($filename, $lines);
		}

		private function getAndroidHome() {
			$home = getenv("ANDROID_HOME");
			$this->assertNotEmpty($home, "Must define ANDROID_HOME env variable");
			return $home;
		}

		/**
		 * Asserts that the apk is zipaligned(4)
		 *
		 * @param $filename
		 */
		private function assertZipaligned($filename) {
			$home = $this->getAndroidHome();
			exec("${home}/build-tools/19.1.0/zipalign -c 4 '$filename'", $output, $retval);
			$this->assertEquals(0, $retval, "APK file was not zipaligned");
		}

		/**
		 * Assert that zip is signed by a specific CN
		 *
		 * @param $filename
		 * @param $cn
		 */
		private function assertSignedByCN($filename, $cn) {
			exec("jarsigner -certs -verbose -verify '{$filename}'", $output);
			$this->assertContains("jar verified.", $output, "Downloaded APK is not signed");
			$this->assertContains("CN=${cn},", implode("\n", $output), "Download APK is signed with another key");
		}

		private function tryGradle($wrapper, $plugin) {

			$android = $this->getAndroidHome() . "/tools/android";

			// create an empty project first
			$TEST_DIR = "/tmp/gradle-test";
			system("rm -rf $TEST_DIR");
			system("cp -R " . __DIR__ . "/../sample-android-app/TestApplication $TEST_DIR"); // copy skeleton app

			// create a certificate for this project
			$time = time();
			$dname = "CN=${time},OU=organizational_unit,O=organization,L=locality,S=state,C=US";
			system("keytool -genkey -keystore ${TEST_DIR}/random.keystore -alias android_app -keyalg RSA -keysize 2048 -validity 3650 -keypass 'swordfish' -storepass 'swordfish' -dname '$dname' 2>&1");

			$this->changeDistributionUrl("$TEST_DIR/gradle/wrapper/gradle-wrapper.properties", $wrapper);
			$this->fixupBuildGradle("$TEST_DIR/build.gradle", "random.keystore");

			// check plugin loaded successfully
			exec("cd $TEST_DIR; bash gradlew tasks", $output);
print_r($output); exit;
			$this->assertContains("testfairyRelease - Uploads the Release build to TestFairy", $output);
			$this->assertContains("testfairyDebug - Uploads the Debug build to TestFairy", $output);

			// try testfairyRelease task
			$output = array();
			exec("cd $TEST_DIR; bash gradlew testfairyRelease --debug", $output);

			// make sure it uploaded successfully to testfairy
			$found = null;
			foreach($output as $line) {
				if (preg_match("/Successfully uploaded to TestFairy, build is available at:/", $line)) {
					$found = true;
					break;
				}
			}

			$this->assertNotNull($found, "Compilation failed");

			$signedUrl = null;
			foreach($output as $line) {
				if (preg_match("/Signed instrumented file is available at: (.+)/", $line, $match)) {
					$signedUrl = $match[1];
					break;
				}
			}

			$this->assertNotNull($signedUrl, "Could not find signed instrumented file url in debug logs");


			$apkFilePath = "${TEST_DIR}/signed.apk";

			// fetch signed apk
			copy($signedUrl, $apkFilePath);

			// make sure app is signed
			$this->assertSignedByCN($apkFilePath, $time);
			$this->assertZipAligned($apkFilePath);
		}

                public function testAndroidPlugin_2_3_0() { 
			$this->tryGradle("3.3", "2.3.0");
		}
	}
