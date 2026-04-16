package org.herb.service.impl;

import org.herb.mapper.KnowledgeGraphMapper;
import org.herb.pojo.*;
import org.herb.service.KnowledgeGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    @Autowired
    private KnowledgeGraphMapper knowledgeGraphMapper;

    @Override
    public GraphData getGraphData() {
        List<Herb> herbs = knowledgeGraphMapper.getAllHerbs();
        List<Prescription> prescriptions = knowledgeGraphMapper.getAllPrescriptions();

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphLink> links = new ArrayList<>();
        List<GraphCategory> categories = Arrays.asList(
                new GraphCategory("中药"),
                new GraphCategory("方剂"),
                new GraphCategory("病症"),
                new GraphCategory("归经"),
                new GraphCategory("功效")
        );

        Map<String, Long> nodeIndexMap = new HashMap<>();
        long nodeId = 0;

        for (Herb herb : herbs) {
            GraphNode node = new GraphNode();
            node.setId(nodeId);
            node.setName(herb.getCnName());
            node.setCategory("中药");
            node.setDescription("性:" + herb.getProperty() + " 味:" + herb.getFlavor() + " 功效:" + herb.getEfficacy());
            nodes.add(node);
            nodeIndexMap.put("中药:" + herb.getCnName(), nodeId++);
        }

        for (Prescription pre : prescriptions) {
            GraphNode node = new GraphNode();
            node.setId(nodeId);
            node.setName(pre.getPreName());
            node.setCategory("方剂");
            node.setDescription("主治:" + pre.getTreatment());
            nodes.add(node);
            nodeIndexMap.put("方剂:" + pre.getPreName(), nodeId++);
        }

        for (int i = 0; i < herbs.size(); i++) {
            Herb herb = herbs.get(i);
            long herbNodeId = i;

            if (herb.getEfficacy() != null && !herb.getEfficacy().isEmpty()) {
                String[] efficacies = herb.getEfficacy().split("、|,|，");
                for (String efficacy : efficacies) {
                    String efficacyName = efficacy.trim();
                    if (efficacyName.isEmpty()) continue;

                    String key = "功效:" + efficacyName;
                    long efficacyNodeId;
                    if (!nodeIndexMap.containsKey(key)) {
                        GraphNode efficacyNode = new GraphNode();
                        efficacyNode.setId(nodeId);
                        efficacyNode.setName(efficacyName);
                        efficacyNode.setCategory("功效");
                        efficacyNode.setDescription(efficacyName);
                        nodes.add(efficacyNode);
                        efficacyNodeId = nodeId++;
                        nodeIndexMap.put(key, efficacyNodeId);
                    } else {
                        efficacyNodeId = nodeIndexMap.get(key);
                    }

                    GraphLink link = new GraphLink();
                    link.setSource(herbNodeId);
                    link.setTarget(efficacyNodeId);
                    link.setRelation("具有功效");
                    links.add(link);
                }
            }

            if (herb.getMeridianTropism() != null && !herb.getMeridianTropism().isEmpty()) {
                String[] meridians = herb.getMeridianTropism().split("、|,|，");
                for (String meridian : meridians) {
                    String meridianName = meridian.trim().replace("经", "");
                    if (meridianName.isEmpty()) continue;

                    String key = "归经:" + meridianName;
                    long meridianNodeId;
                    if (!nodeIndexMap.containsKey(key)) {
                        GraphNode meridianNode = new GraphNode();
                        meridianNode.setId(nodeId);
                        meridianNode.setName(meridianName);
                        meridianNode.setCategory("归经");
                        meridianNode.setDescription(meridianName + "经");
                        nodes.add(meridianNode);
                        meridianNodeId = nodeId++;
                        nodeIndexMap.put(key, meridianNodeId);
                    } else {
                        meridianNodeId = nodeIndexMap.get(key);
                    }

                    GraphLink link = new GraphLink();
                    link.setSource(herbNodeId);
                    link.setTarget(meridianNodeId);
                    link.setRelation("归经");
                    links.add(link);
                }
            }
        }

        for (int i = 0; i < prescriptions.size(); i++) {
            Prescription pre = prescriptions.get(i);
            long preNodeId = herbs.size() + i;

            if (pre.getDisease() != null && !pre.getDisease().isEmpty()) {
                String[] diseases = pre.getDisease().split("、|,|，");
                for (String disease : diseases) {
                    String diseaseName = disease.trim();
                    if (diseaseName.isEmpty()) continue;

                    String key = "病症:" + diseaseName;
                    long diseaseNodeId;
                    if (!nodeIndexMap.containsKey(key)) {
                        GraphNode diseaseNode = new GraphNode();
                        diseaseNode.setId(nodeId);
                        diseaseNode.setName(diseaseName);
                        diseaseNode.setCategory("病症");
                        diseaseNode.setDescription("主治：" + diseaseName);
                        nodes.add(diseaseNode);
                        diseaseNodeId = nodeId++;
                        nodeIndexMap.put(key, diseaseNodeId);
                    } else {
                        diseaseNodeId = nodeIndexMap.get(key);
                    }

                    GraphLink link = new GraphLink();
                    link.setSource(preNodeId);
                    link.setTarget(diseaseNodeId);
                    link.setRelation("主治");
                    links.add(link);
                }
            }

            if (pre.getSyndromes() != null && !pre.getSyndromes().isEmpty()) {
                String[] syndromes = pre.getSyndromes().split("、|,|，");
                for (String syndrome : syndromes) {
                    String syndromeName = syndrome.trim();
                    if (syndromeName.isEmpty()) continue;

                    String key = "病症:" + syndromeName;
                    long syndromeNodeId;
                    if (!nodeIndexMap.containsKey(key)) {
                        GraphNode syndromeNode = new GraphNode();
                        syndromeNode.setId(nodeId);
                        syndromeNode.setName(syndromeName);
                        syndromeNode.setCategory("病症");
                        syndromeNode.setDescription("主治证候：" + syndromeName);
                        nodes.add(syndromeNode);
                        syndromeNodeId = nodeId++;
                        nodeIndexMap.put(key, syndromeNodeId);
                    } else {
                        syndromeNodeId = nodeIndexMap.get(key);
                    }

                    GraphLink link = new GraphLink();
                    link.setSource(preNodeId);
                    link.setTarget(syndromeNodeId);
                    link.setRelation("主治");
                    links.add(link);
                }
            }
        }

        GraphData graphData = new GraphData();
        graphData.setNodes(nodes);
        graphData.setLinks(links);
        graphData.setCategories(categories);

        return graphData;
    }
}
