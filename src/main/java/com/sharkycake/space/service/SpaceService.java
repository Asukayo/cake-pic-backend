package com.sharkycake.space.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.space.dto.SpaceAddRequest;
import com.sharkycake.space.dto.SpaceEditRequest;
import com.sharkycake.space.dto.SpaceQueryRequest;
import com.sharkycake.space.dto.SpaceUpdateRequest;
import com.sharkycake.space.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sharkycake.user.entity.User;
import com.sharkycake.space.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author shark
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-02-22 15:16:20
*/
public interface SpaceService extends IService<Space> {

    /** 在调用方的删除事务中原子扣减空间用量。 */
    void decreaseUsage(Long spaceId, Long pictureSize);

    /**
     * 校验空间数据的方法
     * @param space 待校验空间对象
     * @param add   用于区分是创建数据时校验还是编辑时校验
     */
    void validateSpace(Space space,boolean add);

    /**
     * 根据空间级别自动填充限额数据，
     * @param space 空间对象
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 仅供管理员的修改空间操作
     * @param spaceUpdateRequest 空间修改请求封装类
     * @return  是否修改成功
     */
    boolean updateSpace(SpaceUpdateRequest spaceUpdateRequest);

    /**
     * 仅供用户的修改空间操作
     * @param spaceEditRequest  空间编辑请求封装类
     * @param request 用于获取当前登录用户信息
     * @return  是否编辑成功
     */
    boolean editSpace(SpaceEditRequest spaceEditRequest, HttpServletRequest request);

    /**
     * 创建空间操作
     */
    long addSpace(SpaceAddRequest spaceAddRequest, HttpServletRequest request);

    /**
     * 将Space转换为Vo，并且关联对应的UserVo信息
     * @param space  待转换的Space信息
     * @param request 请求参数
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * @param spaceQueryRequest 空间查询请求封装类
     * @param request           HttpServletRequest
     * @return                  返回查询好的信息
     */
    Page<Space> listSpacePage(SpaceQueryRequest spaceQueryRequest, HttpServletRequest request);

    /**
     * @param spacePage 分页查询结果
     * @return          封装好的分页查询结果
     */
    Page<SpaceVO> getListSpaceVO(Page<Space> spacePage);

    /**
     *
     * @param loginUser
     * @param space
     */
    void checkSpaceAuth(User loginUser, Space space);
}
