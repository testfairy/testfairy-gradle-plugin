package com.testfairy.plugins.gradle

import com.android.build.gradle.api.ApplicationVariant
import groovy.json.JsonSlurper
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException

class TestFairyTask extends DefaultTask {

	public ApplicationVariant applicationVariant
	public TestFairyExtension extension

	/**
	 * Make sure ApiKey is configured and not empty.
	 *
	 * @param extension
	 */
	protected void assertValidApiKey(extension) {
		if (extension.getApiKey() == null || extension.getApiKey().equals("")) {
			throw new GradleException("Please configure your TestFairy apiKey before building")
		}
	}

	protected Object post(String url, MultipartEntityBuilder entity, String via) {
		CloseableHttpClient httpClient = buildHttpClient()
		HttpPost post = new HttpPost(url)
		String userAgent = "TestFairy Gradle Plugin 2.0 " + via
		post.addHeader("User-Agent", userAgent)
		post.setEntity(entity.build())
		HttpResponse response = httpClient.execute(post)

		String json = EntityUtils.toString(response.getEntity())
		def parser = new JsonSlurper()
		def parsed = parser.parseText(json)
		if (parsed.status != "ok") {
			throw new GradleException("Failed with json: " + json)
		}

		return parsed
	}

	private CloseableHttpClient buildHttpClient() {
		HttpClientBuilder builder = new HttpClientBuilder()

		// configure proxy (patched by timothy-volvo, https://github.com/timothy-volvo/testfairy-gradle-plugin)
		def proxyHost = System.getProperty("http.proxyHost")
		if (proxyHost != null) {
			def proxyPort = Integer.parseInt(System.getProperty("http.proxyPort"))
			HttpHost proxy = new HttpHost(proxyHost, proxyPort)
			def proxyUser = System.getProperty("http.proxyUser")
			if (proxyUser != null) {
				AuthScope authScope = new AuthScope(proxyUser, proxyPort)
				Credentials credentials = new UsernamePasswordCredentials(proxyUser, System.getProperty("http.proxyPassword"))

				def credentialsProvider = new BasicCredentialsProvider()
				credentialsProvider.setCredentials(authScope, credentials)
				builder.setDefaultCredentialsProvider(credentialsProvider)
			}

			def requestConfig = new RequestConfig()
			requestConfig.proxy = proxy

			builder.setDefaultRequestConfig(new RequestConfig.Builder().setProxy(proxy).build())
		}

		CloseableHttpClient httpClient = builder
				.build()

		return httpClient
	}
}