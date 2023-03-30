package com.hensemlee.bean.resp;


import com.google.common.base.Objects;
import com.hensemlee.bean.ArtifactItem;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * @author hensemlee
 */
@Data
@Builder
public class EffectiveMavenRes {
    private ArtifactVo latestRelease;
    private ArtifactVo latestSnapshot;
    private List<ArtifactVo> artifactItems;

    @Data
    public static class ArtifactVo extends ArtifactItem {
       private String artifactId;
       private String groupId;
       private String version;

       private Date updatedDate;
       private boolean snapshot;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ArtifactVo that = (ArtifactVo) o;
            return artifactId.equals(that.artifactId) && groupId.equals(that.groupId) && version.equals(that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), artifactId, groupId, version);
        }
    }

}