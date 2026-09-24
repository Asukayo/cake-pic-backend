package com.sharkycake.space.service;

import com.sharkycake.space.entity.Space;
import com.sharkycake.user.entity.User;

import java.util.List;
import com.sharkycake.space.dto.analyze.SpaceCategoryAnalyzeRequest;
import com.sharkycake.space.dto.analyze.SpaceRankAnalyzeRequest;
import com.sharkycake.space.dto.analyze.SpaceSizeAnalyzeRequest;
import com.sharkycake.space.dto.analyze.SpaceTagAnalyzeRequest;
import com.sharkycake.space.dto.analyze.SpaceUsageAnalyzeRequest;
import com.sharkycake.space.dto.analyze.SpaceUserAnalyzeRequest;
import com.sharkycake.space.vo.analyze.SpaceCategoryAnalyzeResponse;
import com.sharkycake.space.vo.analyze.SpaceSizeAnalyzeResponse;
import com.sharkycake.space.vo.analyze.SpaceTagAnalyzeResponse;
import com.sharkycake.space.vo.analyze.SpaceUsageAnalyzeResponse;
import com.sharkycake.space.vo.analyze.SpaceUserAnalyzeResponse;

public interface SpaceAnalyzeService {


    /**
     * 分析空间使用量
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    /**
     * 按照分类分组查询图片表的数据，注意查询数据库时只获取需要字段即可
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     * 开发Service服务，从数据库获取标签数据，统计每个标签的图片数量，并按使用次数降序排序
     * @param spaceTagAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 统计图片大小
     * @param spaceSizeAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 分析用户行为
     * @param spaceUserAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    /**
     *
     * @param spaceRankAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
