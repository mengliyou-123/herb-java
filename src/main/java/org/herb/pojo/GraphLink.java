package org.herb.pojo;

import lombok.Data;

@Data
public class GraphLink {
    private Long source;
    private Long target;
    private String relation;
}
