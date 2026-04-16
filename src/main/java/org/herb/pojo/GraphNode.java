package org.herb.pojo;

import lombok.Data;

@Data
public class GraphNode {
    private Long id;
    private String name;
    private String category;
    private String description;
}
