package com.hensemlee.util;

import com.google.common.io.Files;
import com.hensemlee.exception.EasyDeployException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 * @author hensemlee
 * @owner lijun
 * @team POC
 * @since 2023/3/29 19:07
 */
public class POMUtils {

    private static Map<String, String> absolutePathByArtifactId = new HashMap<>(64);
    private static Map<String, String> artifactIdByAbsolutePath = new HashMap<>(64);

    static {
        fillMaps();
    }

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

    public static void updatePomVersion(String pom, String oldVersion,
        String newVersion, List<String> candidatePomFiles)
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
            return;
        }
        Element version = parent.element("version");
        if (Objects.isNull(version)) {
            return;
        }
        if (oldVersion.equals(version.getText())) {
            version.setText(newVersion);
            writeDocument(document, pomFile);
            if (artifactIdByAbsolutePath.containsKey(pom)) {
                System.out.println("\u001B[32m>>>>>>> update " + artifactIdByAbsolutePath.get(pom) + " release version successfully !\u001B[0m");
                candidatePomFiles.add(pom);
            }
        }
    }

    private static void writeDocument(Document document, File pomFile) throws IOException {
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

    @Deprecated
    public static void setPropertyNewVersionByOldVersion(String pom, String oldVersion,
        String newVersion)
        throws XmlPullParserException, IOException {
        Model model = parsePom(pom);
        Properties properties = model.getProperties();
        Set<String> propertyNames = properties.stringPropertyNames();

        propertyNames.forEach(name -> {
            if (properties.getProperty(name).equals(oldVersion)) {
                properties.setProperty(name, newVersion);
            }
        });
        model.setVersion(newVersion);
        model.setProperties(properties);
        try (FileOutputStream out = new FileOutputStream(pom)) {
            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(out, model);
        }
    }

    public static Model parsePom(String pom) throws XmlPullParserException, IOException {
        File file = new File(pom); // 设置要解析的 pom 文件路径
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model;
        try (FileReader fileReader = new FileReader(file)) {
            model = reader.read(fileReader); // 读取 pom 文件并解析到 model 对象中
        }
        if (Objects.isNull(model)) {
            throw new EasyDeployException("未正确解析pom文件");
        }
        return model;
    }

    private static void fillMaps() {
        File rootDir = new File(System.getenv("TARGET_PROJECT_FOLDER"));
        Iterator<File> iterator = Files.fileTraverser().depthFirstPreOrder(rootDir).iterator();
        while (iterator.hasNext()) {
            File file = iterator.next();
            if (file.isFile() && file.getName().equals("pom.xml")) {
                String parentName = file.getParentFile().getName();
                if (DeployUtils.contains(parentName)) {
                    absolutePathByArtifactId.put(parentName, file.getAbsolutePath());
                    artifactIdByAbsolutePath.put(file.getAbsolutePath(), parentName);
                }
            }
        }
    }
}
