package com.hensemlee.executor;

import com.hensemlee.util.DeployUtils;
import com.hensemlee.util.PathUtils;

import java.io.*;
import java.util.*;

import static com.hensemlee.contants.Constants.CURRENT_PATH;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 11:31
 */
public class EasyDeployFixExecutor implements IExecutor {

	@Override
	public void doExecute(List<String> args) {
		args = args.subList(1, args.size());
		Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects(PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH)));
		Set<String> candidateProjects = new HashSet<>();
		List<String> allNeedDeployedProjects = DeployUtils.getAllNeedDeployedProjects();
		Map<String, Set<String>> prompt = new HashMap<>();
		args.forEach(project -> {
			Set<String> promptSet = new HashSet<>();
			absolutePathByArtifactId.forEach((k, v) -> {
				if (project.startsWith("{") && project.endsWith("}")) {
					String format = project.substring(1, project.length() - 1);
					if (k.equals(format)) {
						candidateProjects.add(k);
						promptSet.add(k);
					}
				} else if ((k.contains(project) || k.equals(project)) && !allNeedDeployedProjects.contains(k)) {
					candidateProjects.add(k);
					promptSet.add(k);
				}
			});
			prompt.put(project, promptSet);
		});
		promotion(prompt);
		candidateProjects.forEach(candidate -> {
			try {
				ProcessBuilder processBuilder = new ProcessBuilder("mvn", "-T", "1C", "clean", "install", "-DskipTests", "-Dmaven.javadoc.skip=true");
				String path = absolutePathByArtifactId.get(candidate);
				int index = path.lastIndexOf("/pom.xml");
				processBuilder.directory(new File(path.substring(0, index)));
				processBuilder.redirectErrorStream(true);
				Process process = processBuilder.start();
				InputStream is = process.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println(line);
				}
				InputStream errorStream = process.getErrorStream();
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
				String errorLine;
				while ((errorLine = errorReader.readLine()) != null) {
					System.err.println(errorLine);
				}
				int exitCode = process.waitFor();
				System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>: " + exitCode);
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
		System.exit(1);
	}

	private static void promotion(Map<String, Set<String>> prompt) {
		prompt.forEach((k, v) -> {
			if (v.size() > 1) {
				System.out.println(
						"\u001B[33mproject_name【" + k + "】 matched multi project: " + Arrays.asList(
								v.toArray())
								+ " it may casue deploy failure, please comfirm and try again\u001B[0m");
				System.exit(1);
			}
		});
	}
}
