DOWNLOAD_DIR=/usr/local/bin
JAR_FILE_NAME=easy-deploy-1.0-SNAPSHOT.jar
SHELL_FILE_NAME=easy-deploy

echo "开始下载easy-deploy安装包"

sudo curl https://tezign-assets-test.oss-cn-beijing.aliyuncs.com/easy-deploy-1.0-SNAPSHOT.jar -o ${DOWNLOAD_DIR}/${JAR_FILE_NAME}
sudo curl https://tezign-assets-test.oss-cn-beijing.aliyuncs.com/easy-deploy -o ${DOWNLOAD_DIR}/${SHELL_FILE_NAME}

echo "赋予easy-deploy执行权限"
sudo chmod 777 ${DOWNLOAD_DIR}/${SHELL_FILE_NAME}

echo -e "\033[33m升级完成\033[0m"
