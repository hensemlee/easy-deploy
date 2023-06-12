package com.hensemlee.bean.resp;

import com.hensemlee.bean.ArtifactItem;
import com.hensemlee.bean.Range;
import java.util.List;
import lombok.Data;

@Data
public class JfrogAqlResult {

    private List<ArtifactItem> results;
    private Range range;
}