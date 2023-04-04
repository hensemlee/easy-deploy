package com.hensemlee.contants;

/**
 * @author hensemlee
 * @owner lijun
 * @team POC
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

    public static final String DEFAULT_COMMIT_MSG = "\"upgrade to %s && launch\"";

    public static final String ALL_DEPLOY_FLAG = "ALL";
    public static final String FIX_FLAG = "FIX";

    public static final String PRD_FLAG = "PRD";

    public static final String DEV_FLAG = "DEV";

    public static final String CHAT_FLAG = "CHAT";

    public static int INCREMENT = 1;

    public static final int MAJOR_VERSION_THRESHOLD = 30;
    public static final int MINOR_VERSION_THRESHOLD = 60;
    public static final int PATCH_VERSION_THRESHOLD = 90;

    public static final String OPENAI_API_KEY = "OPENAI_API_KEY";

    public static final String OPENAI_API_HOST_DEFAULT_VALUE = "https://closeai.deno.dev/";

    public static final String OPENAI_API_HOST = "OPENAI_API_HOST";
}
