package com.hensemlee.util;

import com.hensemlee.exception.EasyDeployException;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 12:21
 */
public class MavenUtils {
	public static void install(String path, AtomicBoolean success, boolean cleanSkip) {
		try {
			ProcessBuilder pb = new ProcessBuilder("mvn", "-T", "1C", "clean", "install", "-DskipTests", "-Dmaven.javadoc.skip=true");
			if (cleanSkip) {
				pb = new ProcessBuilder("mvn", "-T", "1C", "install", "-DskipTests", "-Dmaven.javadoc.skip=true");
			}
			pb.directory(new File(path));
			pb.redirectErrorStream(true);
			Process process = pb.start();
			InputStream is = process.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
			int exitCode = process.waitFor();
			System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>: " + exitCode);
			if (exitCode != 0) {
				System.exit(1);
			}
		} catch (IOException e) {
			throw new EasyDeployException(e.getMessage());
		} catch (InterruptedException e) {
			throw new EasyDeployException(e.getMessage());
		}
	}

	public static void deploy(String path, AtomicBoolean success, boolean cleanSkip) {
		try {
			ProcessBuilder pb = new ProcessBuilder("mvn", "-T", "1C", "clean", "deploy", "-DskipTests", "-Dmaven.javadoc.skip=true");
			if (cleanSkip) {
				pb = new ProcessBuilder("mvn", "-T", "1C", "deploy", "-DskipTests", "-Dmaven.javadoc.skip=true");
			}
			pb.directory(new File(path));
			pb.redirectErrorStream(true);
			Process process = pb.start();
			InputStream is = process.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
			int exitCode = process.waitFor();
			System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>: " + exitCode);
			if (exitCode != 0) {
				System.exit(1);
			}
		} catch (IOException e) {
			throw new EasyDeployException(e.getMessage());
		} catch (InterruptedException e) {
			throw new EasyDeployException(e.getMessage());
		}
	}
}
