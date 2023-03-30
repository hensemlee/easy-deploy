package com.hensemlee.util;

import static com.hensemlee.contants.Constants.JFROG_ARTIFACTORY_API_KEY;
import static com.hensemlee.contants.Constants.JFROG_ARTIFACTORY_QUERY_URL;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
}
