package org.herb.controller;

import org.herb.pojo.GraphData;
import org.herb.pojo.Result;
import org.herb.service.KnowledgeGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/graph")
public class KnowledgeGraphController {

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    @GetMapping("/knowledge")
    public Result getKnowledgeGraph() {
        GraphData graphData = knowledgeGraphService.getGraphData();
        return Result.success(graphData);
    }
}
