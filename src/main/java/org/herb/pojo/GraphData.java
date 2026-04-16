package org.herb.pojo;

import lombok.Data;
import java.util.List;

@Data
public class GraphData {
    private List<GraphNode> nodes;
    private List<GraphLink> links;
    private List<GraphCategory> categories;
}
