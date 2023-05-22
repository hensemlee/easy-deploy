package com.hensemlee.bean;

public class Repository {
	private String httpUrl;
	private String sshUrl;
	private String url;

	private String desc;

	private String language;
	private int star;
	private int fork;
	private int index;

	public String getHttpUrl() {
		return httpUrl;
	}

	public Repository setHttpUrl(String httpUrl) {
		this.httpUrl = httpUrl;
		return this;
	}

	public String getSshUrl() {
		return sshUrl;
	}

	public Repository setSshUrl(String sshUrl) {
		this.sshUrl = sshUrl;
		return this;
	}

	public String getUrl() {
		return url;
	}

	public Repository setUrl(String url) {
		this.url = url;
		return this;
	}

	public String getDesc() {
		return desc;
	}

	public Repository setDesc(String desc) {
		this.desc = desc;
		return this;
	}

	public String getLanguage() {
		return language;
	}

	public Repository setLanguage(String language) {
		this.language = language;
		return this;
	}

	public int getStar() {
		return star;
	}

	public Repository setStar(int star) {
		this.star = star;
		return this;
	}

	public int getFork() {
		return fork;
	}

	public Repository setFork(int fork) {
		this.fork = fork;
		return this;
	}

	public int getIndex() {
		return index;
	}

	public Repository setIndex(int index) {
		this.index = index;
		return this;
	}

	@Override
	public String toString() {
		return "Repository{" + "httpUrl='" + httpUrl + '\'' + ", sshUrl='" + sshUrl + '\'' + ", url='" + url + '\''
				+ ", desc='" + desc + '\'' + ", language='" + language + '\'' + ", star=" + star + ", fork=" + fork
				+ ", index=" + index + '}';
	}
}