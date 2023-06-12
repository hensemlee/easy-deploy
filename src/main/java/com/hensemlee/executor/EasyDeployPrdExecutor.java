package com.hensemlee.executor;

import com.google.common.collect.Lists;
import com.hensemlee.util.*;
import org.dom4j.Document;
import org.dom4j.Element;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hensemlee.contants.Constants.*;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 11:31
 */
public class EasyDeployPrdExecutor implements IExecutor {

	@Override
	public void doExecute(List<String> args) {
		Document parentDocument = POMUtils.getParentDocument();
		Element properties = parentDocument.getRootElement().element("properties");
		Element revision = properties.element("revision");
		Element versionApiDelayed = properties.element("version.api-delayed");

		String tempReversion = revision.getText();
		String tempVersionApiDelayed = versionApiDelayed.getText();

		Document apiDelayedDocument = POMUtils.getApiDelayedDocument();
		Element apiProperties = apiDelayedDocument.getRootElement().element("properties");
		Element apiRevision = apiProperties.element("revision");
		String tempAPiRevision = apiRevision.getText();
		Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects(PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH)));
		if (!Objects.equals(tempReversion, DEFAULT_LOCAL_VERSION) || !Objects.equals(tempVersionApiDelayed,
				DEFAULT_LOCAL_VERSION) || !Objects.equals(tempAPiRevision, DEFAULT_LOCAL_VERSION)) {
			revision.setText(DEFAULT_LOCAL_VERSION);
			versionApiDelayed.setText(DEFAULT_LOCAL_VERSION);
			POMUtils.writeDocument(parentDocument, new File(absolutePathByArtifactId.get(PARENT_PROJECT_NAME)));

			apiRevision.setText(DEFAULT_LOCAL_VERSION);
			POMUtils.writeDocument(apiDelayedDocument, new File(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME)));
		}

		List<String> pomFiles = Lists.newArrayList(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME), absolutePathByArtifactId.get(PARENT_PROJECT_NAME));
		System.out.println("\u001B[32m>>>>>>> start to deploy " + pomFiles.size()
				+ " projects below sequencelly: >>>>>>>\u001B[0m");
		pomFiles.forEach(System.out::println);
		Set<String> deployFailureProjects = new HashSet<>();
		System.out.println("\u001B[31m>>>>>>> 即将执行mvn deploy, 默认会先执行mvn clean, 是否需要跳过mvn clean？(mvn clean命令可能会导致整个edp prd命令执行耗时更长，但可防止一定的问题出现)\u001B[0m");
		System.out.print("\u001B[31m输入 'y' 跳过执行mvn clean，或者输入其他字符继续执行mvn clean：\u001B[0m");
		Scanner scanner = new Scanner(System.in);
		String in = scanner.next();
		boolean cleanSkip = false;
		if (in.equalsIgnoreCase("y")) {
			cleanSkip = true;
		}
		for (String pom : pomFiles) {
			AtomicBoolean success = new AtomicBoolean(true);
			MavenUtils.deploy(pom.substring(0, pom.lastIndexOf("/pom.xml")), success, cleanSkip);
			if (!success.get()) {
				deployFailureProjects.add(pom);
			}
		}
		System.out.println("\u001B[32m>>>>>>> deploy情况如下: >>>>>>>\u001B[0m");
		if (deployFailureProjects.isEmpty()) {
			System.out.println(
					"\u001B[32m>>>>>>> all projects deploy successfully >>>>>>>\u001B[0m");
		} else {
			deployFailureProjects.forEach(deploy -> System.out.println(
					"\u001B[31m>>>>>>> " + deploy + " deploy failure !\u001B[0m"));
			System.exit(1);
		}

		System.out.println("\u001B[31m>>>>>>> 已还原版本，是否需要push？ \u001B[0m");
		System.out.print("\u001B[31m输入 'y' push代码，或者输入其他字符结束执行：\u001B[0m");
		String input = scanner.next();
		if (!input.equalsIgnoreCase("y")) {
			System.exit(1);
		}
		GitUtils.commitAndPushCode(PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH)), pomFiles, "\"revert version\"");
		System.exit(1);
	}
}
