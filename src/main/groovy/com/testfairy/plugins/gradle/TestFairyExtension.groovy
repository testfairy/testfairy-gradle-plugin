package com.testfairy.plugins.gradle

import org.gradle.api.Project

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
	private String changelog
	private String digestalg = "SHA1"
	private String sigalg = "MD5withRSA"
	private Boolean notify = true
	private Boolean anonymous = false
	private Boolean shake = false
	private Boolean autoUpdate = false
	private Boolean recordOnBackground = false
	private Boolean uploadProguardMapping = false

	private String serverEndpoint = "https://app.testfairy.com"

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

	void changelog(String value) {
		this.changelog = value
	}

	String getChangelog() {
		return changelog
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

	void shake(Boolean value) {
		this.shake = value;
	}

	Boolean getShake() {
		return this.shake
	}

	void anonymous(Boolean value) {
		this.anonymous = value
	}

	Boolean anonymous() {
		return this.anonymous;
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

	void digestalg(String value) {
		digestalg = value;
	}

	String getDigestalg() {
		return digestalg
	}

	void sigalg(String value) {
		sigalg = value
	}

	String getSigalg() {
		return sigalg
	}
}

