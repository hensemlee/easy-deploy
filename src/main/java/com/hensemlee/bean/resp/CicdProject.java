package com.hensemlee.bean.resp;


import lombok.Data;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023-06-14 16:21
 */
@Data
public class CicdProject {
    private String name;

    private String coderepo;

    private String context;

    private String business;
}
