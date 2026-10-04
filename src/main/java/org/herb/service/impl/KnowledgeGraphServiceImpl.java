package org.herb.service.impl;

import org.herb.mapper.KnowledgeGraphMapper;
import org.herb.pojo.*;
import org.herb.service.KnowledgeGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    @Autowired
    private KnowledgeGraphMapper knowledgeGraphMapper;

    private static final Map<String, Integer> NODE_SIZE_MAP = new HashMap<>();
    static {
        NODE_SIZE_MAP.put("中药", 50);
        NODE_SIZE_MAP.put("方剂", 45);
        NODE_SIZE_MAP.put("中成药", 40);
        NODE_SIZE_MAP.put("病症", 35);
        NODE_SIZE_MAP.put("证候", 32);
        NODE_SIZE_MAP.put("归经", 28);
        NODE_SIZE_MAP.put("功效", 28);
        NODE_SIZE_MAP.put("药性", 25);
        NODE_SIZE_MAP.put("药味", 25);
    }

    @Override
    public GraphData getGraphData() {
        List<Herb> herbs = knowledgeGraphMapper.getAllHerbs();
        List<Prescription> prescriptions = knowledgeGraphMapper.getAllPrescriptions();
        List<Pcm> pcms = knowledgeGraphMapper.getAllPcms();

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphLink> links = new ArrayList<>();
        List<GraphCategory> categories = Arrays.asList(
                new GraphCategory("中药"),
                new GraphCategory("方剂"),
                new GraphCategory("中成药"),
                new GraphCategory("病症"),
                new GraphCategory("证候"),
                new GraphCategory("归经"),
                new GraphCategory("功效"),
                new GraphCategory("药性"),
                new GraphCategory("药味")
        );

        Map<String, Long> nodeIndexMap = new HashMap<>();
        Map<Long, Integer> nodeConnectionCount = new HashMap<>();
        long nodeId = 0;

        for (Herb herb : herbs) {
            GraphNode node = new GraphNode();
            node.setId(nodeId);
            node.setName(herb.getCnName());
            node.setCategory("中药");
            String desc = "";
            if (herb.getProperty() != null) desc += "性" + herb.getProperty();
            if (herb.getFlavor() != null) desc += "，味" + herb.getFlavor();
            if (herb.getEfficacy() != null) desc += "。" + herb.getEfficacy();
            node.setDescription(desc);
            node.setDbId(herb.getId());
            node.setImage(herb.getHerbPic());
            node.setSymbolSize(NODE_SIZE_MAP.get("中药"));
            node.setValue(1);
            nodes.add(node);
            nodeIndexMap.put("中药:" + herb.getCnName(), nodeId);
            nodeConnectionCount.put(nodeId, 0);
            nodeId++;
        }

        for (Prescription pre : prescriptions) {
            GraphNode node = new GraphNode();
            node.setId(nodeId);
            node.setName(pre.getPreName());
            node.setCategory("方剂");
            node.setDescription(pre.getTreatment() != null ? pre.getTreatment() : "");
            node.setDbId(pre.getId());
            node.setSymbolSize(NODE_SIZE_MAP.get("方剂"));
            node.setValue(1);
            nodes.add(node);
            nodeIndexMap.put("方剂:" + pre.getPreName(), nodeId);
            nodeConnectionCount.put(nodeId, 0);
            nodeId++;
        }

        for (Pcm pcm : pcms) {
            GraphNode node = new GraphNode();
            node.setId(nodeId);
            node.setName(pcm.getPcmName());
            node.setCategory("中成药");
            String desc = "";
            if (pcm.getDosageForm() != null) desc += "剂型：" + pcm.getDosageForm();
            if (pcm.getComposition() != null) desc += "。组成：" + pcm.getComposition();
            node.setDescription(desc);
            node.setDbId(pcm.getId());
            node.setSymbolSize(NODE_SIZE_MAP.get("中成药"));
            node.setValue(1);
            nodes.add(node);
            nodeIndexMap.put("中成药:" + pcm.getPcmName(), nodeId);
            nodeConnectionCount.put(nodeId, 0);
            nodeId++;
        }

        for (int i = 0; i < herbs.size(); i++) {
            Herb herb = herbs.get(i);
            long herbNodeId = i;

            if (herb.getProperty() != null && !herb.getProperty().isEmpty()) {
                String[] props = herb.getProperty().split("、|,|，");
                for (String prop : props) {
                    String propName = prop.trim();
                    if (propName.isEmpty()) continue;
                    String key = "药性:" + propName;
                    long propNodeId = getOrCreateNode(nodes, nodeIndexMap, nodeConnectionCount, 
                            key, propName, "药性", propName + "性", null, nodeId, NODE_SIZE_MAP.get("药性"));
                    if (propNodeId == nodeId) nodeId++;
                    addLink(links, nodeConnectionCount, herbNodeId, propNodeId, "药性为", "herb-property");
                }
            }

            if (herb.getFlavor() != null && !herb.getFlavor().isEmpty()) {
                String[] flavors = herb.getFlavor().split("、|,|，");
                for (String flavor : flavors) {
                    String flavorName = flavor.trim();
                    if (flavorName.isEmpty()) continue;
                    String key = "药味:" + flavorName;
                    long flavorNodeId = getOrCreateNode(nodes, nodeIndexMap, nodeConnectionCount,
                            key, flavorName, "药味", flavorName + "味", null, nodeId, NODE_SIZE_MAP.get("药味"));
                    if (flavorNodeId == nodeId) nodeId++;
                    addLink(links, nodeConnectionCount, herbNodeId, flavorNodeId, "药味为", "herb-flavor");
                }
            }

            if (herb.getEfficacy() != null && !herb.getEfficacy().isEmpty()) {
                String[] efficacies = splitByDelimiters(herb.getEfficacy());
                for (String efficacy : efficacies) {
                    String efficacyName = efficacy.trim();
                    if (efficacyName.isEmpty()) continue;
                    String key = "功效:" + efficacyName;
                    long efficacyNodeId = getOrCreateNode(nodes, nodeIndexMap, nodeConnectionCount,
                            key, efficacyName, "功效", efficacyName, null, nodeId, NODE_SIZE_MAP.get("功效"));
                    if (efficacyNodeId == nodeId) nodeId++;
                    addLink(links, nodeConnectionCount, herbNodeId, efficacyNodeId, "具有功效", "herb-efficacy");
                }
            }

            if (herb.getMeridianTropism() != null && !herb.getMeridianTropism().isEmpty()) {
                String[] meridians = splitByDelimiters(herb.getMeridianTropism());
                for (String meridian : meridians) {
                    String meridianName = meridian.trim();
                    if (meridianName.isEmpty()) continue;
                    if (!meridianName.endsWith("经")) {
                        meridianName = meridianName + "经";
                    }
                    String key = "归经:" + meridianName;
                    long meridianNodeId = getOrCreateNode(nodes, nodeIndexMap, nodeConnectionCount,
                            key, meridianName, "归经", meridianName, null, nodeId, NODE_SIZE_MAP.get("归经"));
                    if (meridianNodeId == nodeId) nodeId++;
                    addLink(links, nodeConnectionCount, herbNodeId, meridianNodeId, "归经于", "herb-meridian");
                }
            }

            if (herb.getIndications() != null && !herb.getIndications().isEmpty()) {
                String[] indications = splitByDelimiters(herb.getIndications());
                for (String indication : indications) {
                    String indName = indication.trim();
                    if (indName.isEmpty() || indName.length() < 2) continue;
                    String key = "病症:" + indName;
                    long indNodeId = getOrCreateNode(nodes, nodeIndexMap, nodeConnectionCount,
                            key, indName, "病症", "主治：" + indName, null, nodeId, NODE_SIZE_MAP.get("病症"));
                    if (indNodeId == nodeId) nodeId++;
                    addLink(links, nodeConnectionCount, herbNodeId, indNodeId, "可治疗", "herb-disease");
                }
            }
        }

        for (int i = 0; i < prescriptions.size(); i++) {
            Prescription pre = prescriptions.get(i);
            long preNodeId = herbs.size() + i;

            if (pre.getDisease() != null && !pre.getDisease().isEmpty()) {
                String[] diseases = splitByDelimiters(pre.getDisease());
                for (String disease : diseases) {
                    String diseaseName = disease.trim();
                    if (diseaseName.isEmpty()) continue;
                    String key = "病症:" + diseaseName;
                    long diseaseNodeId = getOrCreateNode(nodes, nodeIndexMap, nodeConnectionCount,
                            key, diseaseName, "病症", "主治：" + diseaseName, null, nodeId, NODE_SIZE_MAP.get("病症"));
                    if (diseaseNodeId == nodeId) nodeId++;
                    addLink(links, nodeConnectionCount, preNodeId, diseaseNodeId, "主治", "pre-disease");
                }
            }

            if (pre.getSyndromes() != null && !pre.getSyndromes().isEmpty()) {
                String[] syndromes = splitByDelimiters(pre.getSyndromes());
                for (String syndrome : syndromes) {
                    String syndromeName = syndrome.trim();
                    if (syndromeName.isEmpty()) continue;
                    String key = "证候:" + syndromeName;
                    long syndromeNodeId = getOrCreateNode(nodes, nodeIndexMap, nodeConnectionCount,
                            key, syndromeName, "证候", syndromeName, null, nodeId, NODE_SIZE_MAP.get("证候"));
                    if (syndromeNodeId == nodeId) nodeId++;
                    addLink(links, nodeConnectionCount, preNodeId, syndromeNodeId, "主治证候", "pre-syndrome");
                }
            }

            if (pre.getSymptom() != null && !pre.getSymptom().isEmpty()) {
                String symptomText = pre.getSymptom();
                for (Herb herb : herbs) {
                    if (symptomText.contains(herb.getCnName()) || herb.getCnName().length() >= 2 && symptomText.contains(herb.getCnName().substring(0, 2))) {
                        Long herbId = nodeIndexMap.get("中药:" + herb.getCnName());
                        if (herbId != null) {
                            boolean linkExists = links.stream().anyMatch(l -> 
                                (l.getSource() == preNodeId && l.getTarget() == herbId) ||
                                (l.getSource() == herbId && l.getTarget() == preNodeId));
                            if (!linkExists) {
                                addLink(links, nodeConnectionCount, preNodeId, herbId, "包含", "pre-herb");
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < pcms.size(); i++) {
            Pcm pcm = pcms.get(i);
            long pcmNodeId = herbs.size() + prescriptions.size() + i;

            if (pcm.getComposition() != null && !pcm.getComposition().isEmpty()) {
                String compText = pcm.getComposition();
                for (Herb herb : herbs) {
                    if (compText.contains(herb.getCnName())) {
                        Long herbId = nodeIndexMap.get("中药:" + herb.getCnName());
                        if (herbId != null) {
                            addLink(links, nodeConnectionCount, pcmNodeId, herbId, "主要成分", "pcm-herb");
                        }
                    }
                }
            }
        }

        for (GraphNode node : nodes) {
            int count = nodeConnectionCount.getOrDefault(node.getId(), 0);
            int baseSize = NODE_SIZE_MAP.getOrDefault(node.getCategory(), 30);
            int adjustedSize = Math.min(baseSize + count * 2, baseSize + 25);
            node.setSymbolSize(adjustedSize);
            node.setValue(count);
        }

        GraphData graphData = new GraphData();
        graphData.setNodes(nodes);
        graphData.setLinks(links);
        graphData.setCategories(categories);

        return graphData;
    }

    private String[] splitByDelimiters(String text) {
        return text.split("、|,|，|；|;|\\s+");
    }

    private long getOrCreateNode(List<GraphNode> nodes, Map<String, Long> nodeIndexMap, 
                                  Map<Long, Integer> connectionCount, String key, String name, 
                                  String category, String description, String image, long currentId, int size) {
        if (nodeIndexMap.containsKey(key)) {
            return nodeIndexMap.get(key);
        }
        GraphNode node = new GraphNode();
        node.setId(currentId);
        node.setName(name);
        node.setCategory(category);
        node.setDescription(description);
        node.setImage(image);
        node.setSymbolSize(size);
        node.setValue(1);
        nodes.add(node);
        nodeIndexMap.put(key, currentId);
        connectionCount.put(currentId, 0);
        return currentId;
    }

    private void addLink(List<GraphLink> links, Map<Long, Integer> connectionCount,
                          long source, long target, String relation, String relationType) {
        boolean exists = links.stream().anyMatch(l -> 
            (l.getSource() == source && l.getTarget() == target) ||
            (l.getSource() == target && l.getTarget() == source));
        if (exists) return;
        
        GraphLink link = new GraphLink();
        link.setSource(source);
        link.setTarget(target);
        link.setRelation(relation);
        link.setRelationType(relationType);
        link.setValue(1);
        links.add(link);
        
        connectionCount.merge(source, 1, Integer::sum);
        connectionCount.merge(target, 1, Integer::sum);
    }
}
