package com.hensemlee.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import static com.hensemlee.contants.Constants.TARGET_PROJECT_FOLDER;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023/5/29 16:57
 */
public class PathUtils {

	public static String tryGetCurrentExecutionPath(String currentPath) {
		String targetProjectFolder = System.getenv(TARGET_PROJECT_FOLDER);
		if (!StrUtil.isBlank(currentPath)) {
			boolean backendExist = FileUtil.exist(currentPath + "/" + "backend");
			boolean apiDelayedExist = FileUtil.exist(currentPath + "/" + "backend/api-delayed");
			boolean parentExist = FileUtil.exist(currentPath + "/" + "backend/intelligence-parent");
			if (backendExist && apiDelayedExist && parentExist) {
				targetProjectFolder = currentPath;
			}
		}
		return targetProjectFolder;
	}
}
