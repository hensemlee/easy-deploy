package com.hensemlee.executor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.hensemlee.bean.resp.CicdProject;
import com.hensemlee.util.CicdUtils;
import com.hensemlee.util.GitUtils;
import com.hensemlee.util.MavenUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hensemlee.contants.Constants.DEFAULT_REPO_FOLDER_PREFIX;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023-06-14 16:15
 */
@Slf4j
public class EasyDeployEnforceInitDataExecutor implements IExecutor {

	@Override
	public void doExecute(List<String> args) {
		AtomicInteger current = new AtomicInteger(1);
		System.out.println("\u001B[32m请输入cicd token: \u001B[0m");
		Scanner scanner = new Scanner(System.in);
		String token = scanner.nextLine();
		List<CicdProject> projects;
		try {
			projects = CicdUtils.retrieveAllCicdProjects(current.get(), token);
			while (!CollUtil.isEmpty(projects)) {
				handlerProjects(projects);
				projects = CicdUtils.retrieveAllCicdProjects(current.addAndGet(1), token);
			}
		} catch (Exception e) {
			log.error("EasyDeployEnforceInitDataExecutor error occurred", e);
		}
		System.exit(1);
	}

	private void handlerProjects(List<CicdProject> projects) {
		for (CicdProject project : projects) {
			try {
				String coderepo = project.getCoderepo();
				if (StrUtil.isBlank(coderepo) || coderepo.equals("https://git.tezign.com/engineering/dam-sql")) {
					System.err.println("\u001B[31m " + JSON.toJSONString(project) +" exist null info !\u001B[0m");
					continue;
				}
				String nameWithSuffix = coderepo.substring(coderepo.lastIndexOf("/") + 1, coderepo.length());
				String pathname = System.getProperty("user.home") + DEFAULT_REPO_FOLDER_PREFIX
						+ nameWithSuffix.substring(0, nameWithSuffix.lastIndexOf(".git"));
				File directory = new File(pathname);
				if (!directory.exists()) {
					String targetDir = System.getProperty("user.home") + DEFAULT_REPO_FOLDER_PREFIX;
					if (!new File(targetDir).exists()) {
						new File(targetDir).mkdirs();
					}
					System.out.println("\u001B[32mstart to clone " + project.getCoderepo() + " \u001B[0m");
					GitUtils.gitClone(targetDir, project.getCoderepo());
					if (project.getCoderepo().equals("https://git.tezign.com/engineering/tezign-intelligence.git")) {
						System.out.println("================暂停！！！手动修改version!!!");
					}
				}
				System.out.println("\u001B[32m start to init enforce data " + project.getCoderepo() + " \u001B[0m");
				int exitCode = MavenUtils.generalCommand(Objects.nonNull(project.getContext()) ? pathname + "/" + project.getContext() : pathname,
						"mvn", "-T", "1C", "com.tezign.plugin:mvn-plugin-rule:1.0-SNAPSHOT:tzBanDuplicatePomDependencyVersions", "-Dbranch=master");
				if (exitCode != 0) {
					System.out.println("\u001B[31m " + project.getCoderepo() + " init enforce data failed !\u001B[0m");
					System.exit(1);
				}
			} catch (Exception e) {
				log.error("EasyDeployEnforceInitDataExecutor error occurred", e);
			}
		}
	}
}
