package com.hensemlee.util;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.google.common.io.Files;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static com.hensemlee.contants.Constants.JFROG_ARTIFACTORY_API_KEY;
import static com.hensemlee.contants.Constants.JFROG_ARTIFACTORY_QUERY_URL;

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

	public static Map<String, String> findAllMavenProjects(String targetProjectFolder) {
		Map<String, String> absolutePathByArtifactId = new HashMap<>(64);
		File rootDir = new File(targetProjectFolder);
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

	public static void installToLocal(List<String> pomFiles) {
		Set<String> deployFailureProjects = new HashSet<>();
		for (String pom : pomFiles) {
			File directory = new File(pom.substring(0, pom.lastIndexOf("/pom.xml")));
			ProcessBuilder pb = new ProcessBuilder("mvn", "install", "-DskipTests");
			pb.directory(directory);
			pb.redirectErrorStream(true);
			// 启动进程并等待完成
			try {
				Process process = pb.start();
				int exitCode = process.waitFor();
				if (exitCode == 0) {
					BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
					String line;
					while ((line = reader.readLine()) != null) {
						System.out.println(line);
					}
				} else {
					deployFailureProjects.add(pom);
					System.err.println(
							"\u001B[31mmvn install failed with exit code " + exitCode + "!\u001B[0m");
					BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
					String line;
					while ((line = errorReader.readLine()) != null) {
						System.err.println("\u001B[31m" +  line + " \u001B[0m");
					}
				}
			} catch (IOException | InterruptedException e) {
				deployFailureProjects.add(pom);
				e.printStackTrace();
			}
		}
		System.out.println("\u001B[32m>>>>>>> install情况如下: >>>>>>>\u001B[0m");
		if (deployFailureProjects.isEmpty()) {
			System.out.println(
					"\u001B[32m>>>>>>> all projects install successfully >>>>>>>\u001B[0m");
		} else {
			deployFailureProjects.forEach(deploy -> System.out.println(
					"\u001B[31m>>>>>>> " + deploy + " install failure !\u001B[0m"));
		}
	}
}
