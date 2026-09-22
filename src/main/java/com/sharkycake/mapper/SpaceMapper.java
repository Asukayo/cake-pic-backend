package com.sharkycake.mapper;

import com.sharkycake.model.entity.Space;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
* @author shark
* @description 针对表【space(空间)】的数据库操作Mapper
* @createDate 2026-02-22 15:16:20
* @Entity com.sharkycake.model.entity.Space
*/
public interface SpaceMapper extends BaseMapper<Space> {

    int decreaseUsage(
            @Param("spaceId") Long spaceId,
            @Param("picSize") Long picSize
    );

}




