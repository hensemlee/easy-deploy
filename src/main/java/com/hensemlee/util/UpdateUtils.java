package com.hensemlee.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023/5/29 17:38
 */
@Slf4j
public class UpdateUtils {
	public static void update() throws IOException, InterruptedException {
		System.out.println("\u001B[32m请输入sudo密码: \u001B[0m");
		Scanner scanner = new Scanner(System.in);
		String password = scanner.nextLine();
		String[] sudoCommand = {"/bin/bash", "-c", "echo '" + password + "' | sudo -S "
				+ "curl https://tezign-assets-test.oss-cn-beijing.aliyuncs.com/easy-deploy-1.0-SNAPSHOT.jar -o /usr/local/bin/easy-deploy-1.0-SNAPSHOT.jar"
				+ "&& curl https://tezign-assets-test.oss-cn-beijing.aliyuncs.com/easy-deploy -o /usr/local/bin/easy-deploy"
				+ "&& chmod 777 /usr/local/bin/easy-deploy"};
		ProcessBuilder pb = new ProcessBuilder(sudoCommand);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		InputStream is = process.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println(line);
		}
		int exitCode = process.waitFor();
		if (exitCode == 0) {
			System.out.println("\u001B[32mupdate successfully\u001B[0m");
		} else {
			System.out.println("\u001B[31mupdate failed: " + exitCode + "\u001B[0m");
		}
	}
}
