package com.sharkycake.proofing.mapper;

import com.sharkycake.proofing.entity.ProofingProject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
* @author shark
* @description 针对表【proofing_project】的数据库操作Mapper
* @createDate 2026-09-24 16:07:53
* @Entity com.sharkycake.proofing.entity.ProofingProject
*/
public interface ProofingProjectMapper extends BaseMapper<ProofingProject> {

    ProofingProject selectForUpdate(@Param("id") Long id);
}




