package com.testfairy.plugins.gradle

import org.gradle.api.*

class TestFairyExtension {

	private String apiKey
	private String serverEndpoint = "https://app.testfairy.com"

	TestFairyExtension(Project project) {
	}	

	void apiKey(String value) {
		this.apiKey = value
	}

	String getApiKey() {
		return apiKey
	}

	void serverEndpoint(String value) {
		this.serverEndpoint = value
	}

	String getServerEndpoint() {
		return serverEndpoint
	}
		
}

