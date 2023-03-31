package com.hensemlee.util;

import static com.hensemlee.contants.Constants.DEFAULT_COMMIT_MSG;
import static com.hensemlee.contants.Constants.GIT_CMD;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author hensemlee
 * @owner lijun
 * @team POC
 * @since 2023/3/31 10:58
 */
@Slf4j
public class GitUtils {

    public static void commitAndPushCode(String repoPath, List<String> commitPomFiles, String releaseVersion)
        throws IOException, InterruptedException {

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
        builder.append(String.format(DEFAULT_COMMIT_MSG, releaseVersion));
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
    }
}
