package com.sharkycake.space.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sharkycake.space.dto.member.SpaceUserAddRequest;
import com.sharkycake.space.dto.member.SpaceUserQueryRequest;
import com.sharkycake.space.entity.SpaceUser;
import com.sharkycake.user.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sharkycake.space.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author shark
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2026-03-25 17:28:14
*/
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 当前用户退出团队，仅移除自己的成员关系；空间创建者不可退出。
     */
    void leaveTeam(Long spaceId, User loginUser);

    /**
     * 创建spaceUser记录
     * @param spaceUserAddRequest
     * @return
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 校验空间成员对象
     * @param spaceUser
     * @param add   用于区分是创建数据时校验还是编辑时校验，判断条件不一样
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 用于返回拼接好的lambda语句
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);


    /**
     * SpaceUserVO关联对应的SpaceVO和UserVO信息
     * @param spaceUser
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 封装查询类列表
     * @param spaceUserList
     * @param request
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList, HttpServletRequest request);

}
