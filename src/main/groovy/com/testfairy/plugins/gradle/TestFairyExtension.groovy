package com.testfairy.plugins.gradle

import org.gradle.api.*

class TestFairyExtension {

	private String apiKey
	private Boolean iconWatermark = false
	private String video = "on"
	private String videoQuality = "high"
	private String videoRate = "1.0"
	private String testersGroups
	private String maxDuration
	private String metrics
	private String comment
	private Boolean notify = true
	private Boolean autoUpdate = false
	private Boolean recordOnBackground = false
	private Boolean uploadProguardMapping = false

	private String serverEndpoint = "https://upload.testfairy.com"

	TestFairyExtension(Project project) {
	}	

	void apiKey(String value) {
		this.apiKey = value
	}

	String getApiKey() {
		return apiKey
	}

	void iconWatermark(Boolean watermark) {
		this.iconWatermark = watermark
	}

	Boolean getIconWatermark() {
		return iconWatermark
	}

	void video(String video) {
		this.video = video
	}

	String getVideo() {
		return video
	}

	void videoQuality(String value) {
		this.videoQuality = value
	}

	String getVideoQuality() {
		return videoQuality
	}

	void videoRate(String value) {
		this.videoRate = value
	}

	String getVideoRate() {
		return videoRate
	}

	void testersGroups(String value) {
		this.testersGroups = value
	}

	String getTestersGroups() {
		return testersGroups
	}

	void metrics(String value) {
		this.metrics = value
	}

	String getMetrics() {
		return metrics
	}

	void maxDuration(String value) {
		this.maxDuration = value
	}

	String getMaxDuration() {
		return maxDuration
	}

	void comment(String value) {
		this.comment = value
	}

	String getComment() {
		return comment
	}

	void serverEndpoint(String value) {
		this.serverEndpoint = value
	}

	String getServerEndpoint() {
		return serverEndpoint
	}

	void notify(Boolean value) {
		this.notify = value
	}

	Boolean getNotify() {
		return notify
	}

	void autoUpdate(Boolean value) {
		this.autoUpdate = value;
	}

	Boolean getAutoUpdate() {
		return autoUpdate;
	}

	void recordOnBackground(Boolean value) {
		this.recordOnBackground = value;
	}

	Boolean getRecordOnBackground() {
		return recordOnBackground;
	}

    void uploadProguardMapping(Boolean value){
        this.uploadProguardMapping = value;
    }

    Boolean getUploadProguardMapping(){
        return uploadProguardMapping;
    }
}

