package com.sharkycake.picture.mapper;

import com.sharkycake.picture.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
* @author shark
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2025-12-21 20:52:04
* @Entity com.sharkycake.picture.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

//    显式 SQL 可以查询已删除记录。
//    原来的普通 list(wrapper) 会自动过滤逻辑删除数据，与 isDelete = 1 冲突。
    @Select("SELECT * FROM picture " +
            "WHERE isDelete = 1 AND id > #{afterId} " +
            "ORDER BY id ASC LIMIT #{limit}")
    List<Picture> selectDeletedAfterId(
            @Param("afterId") long afterId,
            @Param("limit") int limit);

}




