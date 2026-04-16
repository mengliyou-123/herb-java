package org.herb.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.herb.pojo.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeGraphMapper {

    @Select("select * from herb")
    List<Herb> getAllHerbs();

    @Select("select * from prescription")
    List<Prescription> getAllPrescriptions();

    @Select("select * from pcm")
    List<Pcm> getAllPcms();
}
