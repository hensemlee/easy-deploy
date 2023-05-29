package com.hensemlee.util;

import com.hensemlee.exception.EasyDeployException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import static com.hensemlee.contants.Constants.*;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023/3/29 19:07
 */
public class POMUtils {

    public static void updateParentPomVersion(String pom, String oldVersion, String newVersion) throws IOException {
        File pomFile = new File(pom);
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(pomFile);
        } catch (DocumentException e) {
            throw new EasyDeployException("Error reading pom.xml: " + e.getMessage());
        }
        // 获取要修改的属性值
        Element properties = document.getRootElement().element("properties");
        List<Element> elements = properties.elements();
        elements.forEach(element -> {
            String text = element.getText();
            if (oldVersion.equals(text)) {
                element.setText(newVersion);
            }
        });
        Element version = document.getRootElement().element("version");
        if (Objects.isNull(version)) {
            return;
        }
        version.setText(newVersion);
        writeDocument(document, pomFile);
    }


    /**
     * 更新除parent之外的pom文件版本
     *
     * @param pom 要检察官的pom文件
     * @param oldVersion 待更新的版本
     * @param newVersion 更新后的版本
     * @return true 更新成功 false 更新失败或没有需要更新
     * @throws IOException
     */
    public static boolean updatePomVersion(String pom, String oldVersion,
        String newVersion)
        throws IOException {
        File pomFile = new File(pom);
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(pomFile);
        } catch (DocumentException e) {
            throw new EasyDeployException("Error reading pom.xml: " + e.getMessage());
        }
        // 获取要修改的属性值
        Element parent = document.getRootElement().element("parent");
        if (Objects.isNull(parent)) {
            return false;
        }
        Element version = parent.element("version");
        if (Objects.isNull(version)) {
            return false;
        }
        if (oldVersion.equals(version.getText())) {
            version.setText(newVersion);
            writeDocument(document, pomFile);
            return true;
        }
        return false;
    }

    public static boolean checkCandidatePom(String pom, String candidateVersion) {
        File pomFile = new File(pom);
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(pomFile);
        } catch (DocumentException e) {
            throw new EasyDeployException("Error reading pom.xml: " + e.getMessage());
        }
        Element parent = document.getRootElement().element("parent");
        if (Objects.isNull(parent)) {
            return false;
        }
        Element version = parent.element("version");
        if (Objects.isNull(version)) {
            return false;
        }
        if (candidateVersion.equals(version.getText())) {
            return true;
        }
        return false;
    }

	public static void writeDocument(Document document, File pomFile) throws IOException {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(pomFile);
            document.write(fileWriter);
        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
    }

    public static Document getParentDocument() {
		String targetProjectFolder = PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH));
		Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects(targetProjectFolder);
        String parentPOM = absolutePathByArtifactId.get(PARENT_PROJECT_NAME);
        File pomFile = new File(parentPOM);
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(pomFile);
        } catch (DocumentException e) {
            throw new EasyDeployException("Error reading pom.xml: " + e.getMessage());
        }
        if (Objects.isNull(document)) {
            throw new EasyDeployException("parent pom.xml not found");
        }
        return document;
    }

	public static Document getApiDelayedDocument() {
		String targetProjectFolder = PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH));
		Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects(targetProjectFolder);
		String apiDelayedPom = absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME);
		File pomFile = new File(apiDelayedPom);
		SAXReader reader = new SAXReader();
		Document document;
		try {
			document = reader.read(pomFile);
		} catch (DocumentException e) {
			throw new EasyDeployException("Error reading pom.xml: " + e.getMessage());
		}
		if (Objects.isNull(document)) {
			throw new EasyDeployException("parent pom.xml not found");
		}
		return document;
	}
}
