package org.herb.pojo;

import lombok.Data;

@Data
public class GraphNode {
    private Long id;
    private String name;
    private String category;
    private String description;
    private Integer dbId;
    private String image;
    private Integer symbolSize;
    private Integer value;
}
