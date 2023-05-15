package com.hensemlee.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
public class GitHubTrendingUtils {
	public static void top25() throws IOException {
		// daily weekly monthly
		Scanner param = new Scanner(System.in);
		System.out.println("\u001B[32mInput your language:\u001B[0m");
		String lang = param.nextLine();
		if (StrUtil.isBlank(lang)) {
			System.out.println("Invalid language");
			System.exit(-1);
		}
		System.out.println("\u001B[32mChose range date:\u001B[0m");
		System.out.println("\u001B[32m1. daily\u001B[0m");
		System.out.println("\u001B[32m2. weekly\u001B[0m");
		System.out.println("\u001B[32m3. monthly\u001B[0m");
		int range = param.nextInt();
		if (range != 1 && range != 2 && range != 3) {
			System.out.println("Invalid range");
			System.exit(-1);
		}
		String rangeDate = null;
		if (range == 1) {
			rangeDate = "daily";
		}
		if (range == 2) {
			rangeDate = "weekly";
		}
		if (range == 3) {
			rangeDate = "monthly";
		}
		Document doc = Jsoup.connect("https://github.com/trending/" + lang + "?since=" + rangeDate).get();
		List<Repository> top = new ArrayList<>();
		Elements topRepos = doc.select("article.Box-row");
		for (Element topRepo : topRepos) {
			List<Node> nodes = topRepo.childNodes();
			int q = 0;
			int star = 0;
			int fork = 0;
			int index = 0;
			String desc = "";
			String language = "";
			String repoName = "";
			for (Node node : nodes) {
				Element element = null;
				try {
					element = (Element) node;
				} catch (Exception e) {

				}
				if (element == null || q == 1) {
					q++;
					continue;
				}
				if (q == 3) {
					if (Objects.nonNull(element.selectFirst("a[data-view-component=true]")) && Objects.nonNull(
							element.selectFirst("a[data-view-component=true]").attr("href"))) {
						repoName = element.selectFirst("a[data-view-component=true]").attr("href");
					}
				}
				if (q == 5) {
					if (Objects.nonNull(element.text())) {
						desc = element.text();
					}
				}
				if (q == 7) {
					if (Objects.nonNull(element.selectFirst("div span[itemprop=programmingLanguage]"))
							&& Objects.nonNull(element.selectFirst("div span[itemprop=programmingLanguage]").text())) {
						language = element.selectFirst("div span[itemprop=programmingLanguage]").text();
					}

					if (Objects.nonNull(element.selectFirst("a").text())) {
						star = Integer.parseInt(element.selectFirst("a").text().replace(",", ""));
					}
					Elements elements = element.select("a");
					if (!CollectionUtil.isEmpty(elements)) {
						int i = 0;
						for (Element e : elements) {
							if (i == 0) {
								star = Integer.parseInt(e.text().replace(",", ""));
								i++;
							}
							if (i == 1) {
								fork = Integer.parseInt(e.text().replace(",", ""));
								i++;
							}
						}
					}
					if (Objects.nonNull(element.select("span:last-child")) &&
							Objects.nonNull(element.select("span:last-child").last()) &&
							Objects.nonNull(element.select("span:last-child").last().text())) {
						String indexStar = element.select("span:last-child").last().text();
						index = Integer.parseInt(indexStar.substring(0, indexStar.indexOf(" ")).trim().replace(",", ""));
					}
				}
				q++;
			}
			Repository repository = new Repository();
			repository.setDesc(desc);
			repository.setFork(fork);
			repository.setStar(star);
			repository.setIndex(index);
			repository.setLanguage(language);
			repository.setUrl("https://github.com" + repoName);
			repository.setHttpUrl("https://github.com" + repoName + ".git");
			repository.setSshUrl("git@github.com:" + repoName + ".git");
			top.add(repository);
		}

		Scanner scanner = new Scanner(System.in);
		int choice = 0;
		List<Repository> sortedTop = top.stream().sorted(Comparator.comparing(Repository::getIndex).reversed())
				.collect(Collectors.toList());
		for (int i = 0; i < sortedTop.size(); i++) {
			System.out.println(i + ".【" + rangeDate + " stars: " + sortedTop.get(i).index + "】 " + sortedTop.get(i).getDesc() + " (" + sortedTop.get(i).getHttpUrl() + ")");
		}
		System.out.println(sortedTop.size() + ". Quit");
		while (choice != top.size()) {
			try {
				System.out.println("\u001B[32mSelect an option:\u001B[0m");
				choice = scanner.nextInt();
				Scanner select = new Scanner(System.in);
				if (choice >= 0 && choice <= sortedTop.size()) {
					if (choice != sortedTop.size()) {
						System.out.println("You selected Option " + choice);
						System.out.println("clone it ? [y/n]");
						String input = select.nextLine();
						if (input.equalsIgnoreCase("y")) {
							System.out.println("please enter your clone dir");
							String dir = select.nextLine();
							checkDir(dir);
							System.out.println("please chose https or ssh protocol: [h/s]");
							String protocol = select.nextLine();
							if (protocol.equalsIgnoreCase("h")) {
								gitClone(dir, sortedTop.get(choice).httpUrl);
							} else if (protocol.equalsIgnoreCase("s")) {
								gitClone(dir, sortedTop.get(choice).sshUrl);
							} else {
								System.out.println("Invalid input, please try again");
							}
							System.exit(-1);
						} else {
							System.out.println("Invalid input");
							System.exit(-1);
						}
					} else {
						System.out.println("Goodbye!");
						System.exit(-1);
					}
				} else {
					System.out.println("Invalid choice, please try again");
				}
			} catch (Exception e) {
				System.out.println("error occurred: " + e);
				System.exit(-1);
			}
		}

	}

	private static void checkDir(String dir) {
		if (dir.contains("~")) {
			dir = dir.replace("~", System.getProperty("user.home"));
		}
		File directory = new File(dir);
		if (!directory.exists()) {
			System.out.println("dir does not exist, create it? [y/n]");
			Scanner select = new Scanner(System.in);
			String create = select.nextLine();
			if (create.equalsIgnoreCase("y")) {
				if (directory.mkdirs()) {
					System.out.println("Directory created successfully");
				} else {
					System.out.println("Failed to create directory");
					System.exit(-1);
				}
			}
		}
	}

	public static void gitClone(String dir, String repoAddress)
			throws IOException, InterruptedException {
		if (dir.contains("~")) {
			dir = dir.replace("~", System.getProperty("user.home"));
		}
		List<String> commandList = new ArrayList<>();
		commandList.add("sh");
		commandList.add("-c");
		StringBuilder builder = new StringBuilder();
		builder.append("cd");
		builder.append(" ");
		builder.append(dir);
		builder.append(" ");
		builder.append("&&");
		builder.append(" ");
		builder.append("git clone ");
		builder.append(repoAddress);
		commandList.add(builder.toString());
		String[] commands = commandList.toArray(new String[commandList.size()]);
		ProcessBuilder processBuilder = new ProcessBuilder()
				.directory(new File(dir))
				.command(commands);
		processBuilder.redirectErrorStream(true);
		// 启动进程并等待完成
		Process process = processBuilder.start();
		InputStream is = process.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println(line);
		}
		int exitCode = process.waitFor();
		if (exitCode == 0) {
			System.out.println("\u001B[32mclone successfully  \u001B[0m");
		} else {
			System.err.println("\u001B[31mclone failure \u001B[0m");
		}
	}

	public static class Repository {
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
}