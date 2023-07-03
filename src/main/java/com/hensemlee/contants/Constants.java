package com.hensemlee.contants;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023/3/30 14:57
 */
public class Constants {

    public static final String PARENT_PROJECT_NAME = "intelligence-parent";

    public static final String RELEASE_PATTERN = "^\\d+\\.\\d+\\.\\d+\\.RELEASE$";
    public static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    public static final String PATH_DELIMITER = "/";
    public static final String JFROG_ARTIFACTORY_API_KEY = "JFROG_ARTIFACTORY_API_KEY";
    public static final String JFROG_ARTIFACTORY_QUERY_URL = "JFROG_ARTIFACTORY_QUERY_URL";
    public static final String X_JFROG_ART_API_HEADER = "X-JFrog-Art-Api";

    public static final String GIT_CMD = "git";

    public static final String DEFAULT_COMMIT_MSG = "\"upgrade to %s\"";

    public static final String ALL_DEPLOY_FLAG = "ALL";
    public static final String FIX_FLAG = "FIX";

    public static final String PRD_FLAG = "PRD";

    public static final String TEST_FLAG = "TEST";

    public static final String DEV_FLAG = "DEV";

    public static final String GIT_TRENDING_FLAG = "gt";

    public static final String CHAT_FLAG = "CHAT";
    public static final String UPGRADE_FLAG = "UPGRADE";
	
    public static final String CURRENT_PATH = "currentPath";

	public static final String ENFORCE_FLAG = "enforce";
	public static final String ENFORCE_INIT_DATA_FLAG = "init";

    public static int INCREMENT = 1;

    public static final int MAJOR_VERSION_THRESHOLD = 30;
    public static final int MINOR_VERSION_THRESHOLD = 60;
    public static final int PATCH_VERSION_THRESHOLD = 90;

    public static final String OPENAI_API_KEY = "OPENAI_API_KEY";

    public static final String OPENAI_API_HOST_DEFAULT_VALUE = "https://open.aiproxy.xyz/";

    public static final String OPENAI_API_HOST = "OPENAI_API_HOST";

    public static final String TARGET_PROJECT_FOLDER = "TARGET_PROJECT_FOLDER";

	public static final String API_DELAYED_PROJECT_NAME = "api-delayed";

	public static final String DEFAULT_API_DELAYED_VERSION = "API-DELAYED-LOCAL-1.0.0-SNAPSHOT";
	public static final String DEFAULT_REVISION = "DAM-LOCAL-1.0.0-SNAPSHOT";

	public static final String DEFAULT_LOCAL_VERSION = "LOCAL-1.0.0-SNAPSHOT";

    public static final String CICD_HOST = "https://ops.tezign.com/api/v1/cicd/projects";

    public static final String DEFAULT_REPO_FOLDER_PREFIX = "/cicd/repos/";
}
