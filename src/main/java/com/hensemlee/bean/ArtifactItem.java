package com.hensemlee.bean;

import lombok.Data;

@Data
public class ArtifactItem {
    private String repo;
    private String path;
    private String name;
    private String type;
    private String size;
    private String created;
    private String created_by;
    private String modified;
    private String modified_by;
    private String updated;
}