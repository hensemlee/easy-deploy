package com.hensemlee.util;

import com.hensemlee.exception.EasyDeployException;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static com.hensemlee.contants.Constants.GIT_CMD;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023/3/31 10:58
 */
@Slf4j
public class GitUtils {

    public static void commitAndPushCode(String repoPath, List<String> commitPomFiles, String commitMsg) {
		try {
			List<String> commandList = new ArrayList<>();
			commandList.add("sh");
			commandList.add("-c");
			StringBuilder builder = new StringBuilder();
			builder.append("git add ");
			commitPomFiles.forEach(commit -> {
				builder.append(commit);
				builder.append(" ");
			});
			builder.append("&&");
			builder.append(" ");
			builder.append(GIT_CMD);
			builder.append(" ");
			builder.append("commit");
			builder.append(" ");
			builder.append("-m");
			builder.append(" ");
			builder.append(commitMsg);
			builder.append(" ");
			builder.append("&&");
			builder.append(" ");
			builder.append(GIT_CMD);
			builder.append(" ");
			builder.append("push");
			commandList.add(builder.toString());
			String[] commands = commandList.toArray(new String[commandList.size()]);
			ProcessBuilder processBuilder = new ProcessBuilder()
				.directory(new File(repoPath))
				.command(commands);
			// 启动进程并等待完成
			Process process = processBuilder.start();
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				new BufferedReader(new InputStreamReader(process.getInputStream()));
				BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println("\u001B[32m" + line + " \u001B[0m");
				}
			} else {
				System.err.println(
					"\u001B[31mGit command failed with exit code " + exitCode + "!\u001B[0m");
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				String line;
				while ((line = errorReader.readLine()) != null) {
					System.err.println("\u001B[31m" +  line + " \u001B[0m");
				}
			}
		} catch (IOException e) {
			throw new EasyDeployException(e.getMessage());
		} catch (InterruptedException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public static String getCurrentBranch(String repoPath) {
		try {
			List<String> commandList = new ArrayList<>();
			commandList.add("sh");
			commandList.add("-c");
			commandList.add("git rev-parse --abbrev-ref HEAD");
			String[] commands = commandList.toArray(new String[commandList.size()]);
			ProcessBuilder processBuilder = new ProcessBuilder().directory(new File(repoPath))
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
						"\u001B[31mGit command failed with exit code " + exitCode + "!\u001B[0m");
					BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
					String line;
					while ((line = errorReader.readLine()) != null) {
						System.err.println("\u001B[31m" +  line + " \u001B[0m");
					}
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			throw new EasyDeployException(e.getMessage());
		}
		return null;
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
			System.out.println("\u001B[32mClone successfully  \u001B[0m");
		} else {
			System.err.println("\u001B[31mClone failure \u001B[0m");
		}
	}
}
