package com.hensemlee.bean.req;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EffectiveMavenReq {

    private boolean exact;
    private List<String> repos;
    private String artifactId;
    private String groupId;
    private String version;
    private String updated;
    private boolean snapshot;
    private String type;

    public static enum TypeEnum {
        /**
         * jar
         */
        JAR("jar"),
        /**
         * pom
         */
        POM("pom"),
        ;

        private String type;

        TypeEnum(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }
}