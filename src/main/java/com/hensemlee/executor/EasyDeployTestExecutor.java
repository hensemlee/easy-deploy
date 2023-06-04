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
public class EasyDeployTestExecutor implements IExecutor {
	@Override
	public void doExecute(List<String> args) {
		boolean firstUpdate = false;
		String currentExecutionPath = System.getProperty(CURRENT_PATH);
		String targetProjectFolder = PathUtils.tryGetCurrentExecutionPath(currentExecutionPath);
		String currentBranch = GitUtils.getCurrentBranch(targetProjectFolder);
		currentBranch = CommonUtils.removeIllegalChars(currentBranch);
		Document parentDocument = POMUtils.getParentDocument();
		Element properties = parentDocument.getRootElement().element("properties");
		List<Element> elements = properties.elements();
		Element revision = null;
		Element versionApiDelayed = null;

		for (Element element : elements) {
			if (Objects.equals(element.getQName().getName(), "revision")) {
				revision = element;
			}
			if (Objects.equals(element.getQName().getName(), "version.api-delayed")) {
				versionApiDelayed = element;
			}
		}
		if (Objects.isNull(revision) || Objects.isNull(versionApiDelayed)) {
			System.out.println("\u001B[31m>>>>>>> 请检查parent pom.xml文件中是否有revision和version.api-delayed节点 \u001B[0m");
			System.exit(1);
		}

		String tempReversion = revision.getText();
		String tempVersionApiDelayed = versionApiDelayed.getText();

		Document apiDelayedDocument = POMUtils.getApiDelayedDocument();
		Element apiProperties = apiDelayedDocument.getRootElement().element("properties");
		List<Element> apiElements = apiProperties.elements();
		Element apiRevision = null;
		for (Element element : apiElements) {
			if (Objects.equals(element.getQName().getName(), "revision")) {
				apiRevision = element;
			}
		}

		if (Objects.isNull(apiRevision)) {
			System.out.println("\u001B[31m>>>>>>> 请检查api-delayed pom.xml文件中是否有revision节点 \u001B[0m");
			System.exit(1);
		}

		String tempAPiRevision = apiRevision.getText();
		Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects(targetProjectFolder);
		if (!Objects.equals(tempReversion, currentBranch + SNAPSHOT_SUFFIX ) || !Objects.equals(tempVersionApiDelayed, currentBranch + SNAPSHOT_SUFFIX) || !Objects.equals(tempAPiRevision, currentBranch + SNAPSHOT_SUFFIX)) {
			revision.setText(currentBranch + SNAPSHOT_SUFFIX);
			versionApiDelayed.setText(currentBranch + SNAPSHOT_SUFFIX);
			POMUtils.writeDocument(parentDocument, new File(absolutePathByArtifactId.get(PARENT_PROJECT_NAME)));

			apiRevision.setText(currentBranch + SNAPSHOT_SUFFIX);
			POMUtils.writeDocument(apiDelayedDocument,
					new File(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME)));
			firstUpdate = true;
		}

		List<String> pomFiles = Lists.newArrayList(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME), absolutePathByArtifactId.get(PARENT_PROJECT_NAME));
		System.out.println("\u001B[32m>>>>>>> start to deploy " + pomFiles.size()
				+ " projects below sequencelly: >>>>>>>\u001B[0m");
		pomFiles.forEach(System.out::println);
		Set<String> installFailureProjects = new HashSet<>();
		System.out.println("\u001B[31m>>>>>>> 即将执行mvn deploy, 默认会先执行mvn clean, 是否需要跳过mvn clean？(mvn clean命令可能会导致整个edp test命令执行耗时更长，但可防止一定的问题出现)\u001B[0m");
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
				installFailureProjects.add(pom);
			}
		}
		System.out.println("\u001B[32m>>>>>>> deploy情况如下: >>>>>>>\u001B[0m");
		if (installFailureProjects.isEmpty()) {
			System.out.println(
					"\u001B[32m>>>>>>> all projects deploy successfully >>>>>>>\u001B[0m");
		} else {
			installFailureProjects.forEach(deploy -> System.out.println(
					"\u001B[31m>>>>>>> " + deploy + " deploy failure !\u001B[0m"));
			System.exit(1);
		}

		if (firstUpdate) {
			System.out.println("\u001B[31m>>>>>>> 已修改版本，是否需要push？\u001B[0m");
			System.out.print("\u001B[31m输入 'y' push代码，或者输入其他字符结束执行：\u001B[0m");
			String input = scanner.next();
			if (!input.equalsIgnoreCase("y")) {
				System.exit(1);
			}
			GitUtils.commitAndPushCode(System.getenv(targetProjectFolder), pomFiles, String.format(DEFAULT_COMMIT_MSG, currentBranch + SNAPSHOT_SUFFIX));
		}
		System.exit(1);
	}
}
