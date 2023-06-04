package com.hensemlee.executor;

import com.google.common.collect.Lists;
import com.hensemlee.util.DeployUtils;
import com.hensemlee.util.GitUtils;
import com.hensemlee.util.POMUtils;
import com.hensemlee.util.PathUtils;
import org.dom4j.Document;
import org.dom4j.Element;

import java.io.File;
import java.util.*;

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

		System.out.println("\u001B[31m>>>>>>> 已还原版本，是否需要push？ \u001B[0m");
		Scanner scanner = new Scanner(System.in);
		System.out.print("\u001B[31m输入 'y' push代码，或者输入其他字符结束执行：\u001B[0m");
		String input = scanner.next();
		if (!input.equalsIgnoreCase("y")) {
			System.exit(1);
		}
		ArrayList<String> pomFiles = Lists.newArrayList(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME), absolutePathByArtifactId.get(PARENT_PROJECT_NAME));
		GitUtils.commitAndPushCode(PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH)), pomFiles, "\"revert version\"");
		System.exit(1);
	}
}
