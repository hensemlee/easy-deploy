package com.hensemlee.util;

import static com.hensemlee.contants.Constants.JFROG_ARTIFACTORY_API_KEY;
import static com.hensemlee.contants.Constants.JFROG_ARTIFACTORY_QUERY_URL;

import cn.hutool.core.util.StrUtil;
import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.google.common.io.Files;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static com.hensemlee.contants.Constants.*;

/**
 * @author hensemlee
 * @create 2023/3/5 21:34
 */
public class DeployUtils {

	private DeployUtils() {
	}

	private static List<String> orderedDeploySequence;

	private static Config config;

	static {
		config = getConfig();
		orderedDeploySequence = getSequences();
	}

	public static boolean contains(String str) {
		return orderedDeploySequence.contains(str);
	}

	public static int indexOf(String str) {
		return orderedDeploySequence.indexOf(str);
	}

	private static List<String> getSequences() {
		// 后面可能会新增项目需要deploy的项目, 所以会从apollo读取deploy的顺序
		String deploySequenceStr = config.getProperty("deploy-sequence", "");
		if (Objects.isNull(deploySequenceStr) || deploySequenceStr.length() <= 0) {
			throw new NullPointerException("未找到设定的部署顺序!");
		}
		return Arrays.stream(deploySequenceStr.split(","))
				.filter(sequence -> Objects.nonNull(sequence) && sequence.length() > 0).map(String::trim)
				.collect(Collectors.toList());
	}

	public static List<String> getAllNeedDeployedProjects() {
		return orderedDeploySequence;
	}

	public static String getJfrogArtifactoryApiKey() {
		return config.getProperty(JFROG_ARTIFACTORY_API_KEY, "");
	}

	public static String getJfrogArtifactoryURL() {
		return config.getProperty(JFROG_ARTIFACTORY_QUERY_URL, "");
	}

	private static Config getConfig() {
		return ConfigService.getAppConfig();
	}

	public static Map<String, String> findAllMavenProjects() {
		Map<String, String> absolutePathByArtifactId = new HashMap<>(64);
		File rootDir = new File(System.getenv(TARGET_PROJECT_FOLDER));
		Iterator<File> iterator = Files.fileTraverser().depthFirstPreOrder(rootDir).iterator();
		while (iterator.hasNext()) {
			File file = iterator.next();
			if (file.isFile() && file.getName().equals("pom.xml")) {
				String parentName = file.getParentFile().getName();
				absolutePathByArtifactId.put(parentName, file.getAbsolutePath());
			}
		}
		return absolutePathByArtifactId;
	}

	public static String installToLocal(List<String> pomFiles) {
		List<String> commandList = new ArrayList<>();
		commandList.add("sh");
		commandList.add("-c");
		StringBuilder builder = new StringBuilder();
		builder.append("mvn install -f ");
		pomFiles.forEach(commit -> {
			builder.append(commit);
			builder.append(" ");
		});
		commandList.add(builder.toString());
		String[] commands = commandList.toArray(new String[commandList.size()]);
		ProcessBuilder processBuilder = new ProcessBuilder()
				.command(commands);
		// 启动进程并等待完成
		Process process;
		try {
			process = processBuilder.start();
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				new BufferedReader(new InputStreamReader(process.getInputStream()));
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(process.getInputStream()));
				String line;
				while ((line = reader.readLine()) != null) {
					return line;
				}
			} else {
				System.err.println(
						"\u001B[31mmvn install failed with exit code " + exitCode + "!\u001B[0m");
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				String line;
				while ((line = errorReader.readLine()) != null) {
					System.err.println("\u001B[31m" +  line + " \u001B[0m");
				}
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}
}
