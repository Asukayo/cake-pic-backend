package com.sharkycake.space.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.xiaoymin.knife4j.core.util.StrUtil;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.space.dto.SpaceAddRequest;
import com.sharkycake.space.dto.SpaceEditRequest;
import com.sharkycake.space.dto.SpaceQueryRequest;
import com.sharkycake.space.dto.SpaceUpdateRequest;
import com.sharkycake.space.entity.Space;
import com.sharkycake.space.entity.SpaceUser;
import com.sharkycake.user.entity.User;
import com.sharkycake.space.enums.SpaceLevelEnum;
import com.sharkycake.space.enums.SpaceRoleEnum;
import com.sharkycake.space.enums.SpaceTypeEnum;
import com.sharkycake.space.vo.SpaceVO;
import com.sharkycake.user.vo.UserVO;
import com.sharkycake.space.service.SpaceService;
import com.sharkycake.space.mapper.SpaceMapper;
import com.sharkycake.space.service.SpaceUserService;
import com.sharkycake.user.service.UserService;
import org.redisson.RedissonLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.net.BindException;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author shark
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2026-02-22 15:16:20
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    private final UserService userService;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final SpaceUserService spaceUserService;

    public SpaceServiceImpl(UserService userService, RedissonClient redissonClient, TransactionTemplate transactionTemplate,SpaceUserService spaceUserService) {
        this.userService = userService;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        this.spaceUserService = spaceUserService;
    }

    @Override
    public void decreaseUsage(Long spaceId, Long pictureSize) {
        int affectedRows = baseMapper.decreaseUsage(spaceId, pictureSize);
        ThrowUtils.throwIf(affectedRows != 1, ErrorCode.OPERATION_ERROR, "扣减空间额度失败");
    }

    /**
     * @param space 待校验空间对象
     * @param add   用于区分是创建数据时校验还是编辑时校验
     */
    @Override
    public void validateSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从space对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        Integer spaceType = space.getSpaceType();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getSpaceLevelEnumByValue(spaceLevel);
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        // 要创建时校验
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间名称不能为空");
            }
            if (spaceLevelEnum == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间级别不能为空");
            }
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间类型不能为空");
            }
        }
        // 修改数据时，如果要更改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间级别不存在");
        }
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"控件类型不存在");
        }
        if (!StrUtil.isBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间名称过长");
        }

    }

    /**
     * @param space 空间对象
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        if (space == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnumByValue = SpaceLevelEnum.getSpaceLevelEnumByValue(spaceLevel);
        ThrowUtils.throwIf(spaceLevelEnumByValue == null, ErrorCode.PARAMS_ERROR,"空间级别不存在");
        space.setMaxCount(spaceLevelEnumByValue.getMaxCount());
        space.setMaxSize(spaceLevelEnumByValue.getMaxSize());
    }

    /**
     * @param spaceUpdateRequest 空间修改请求封装类
     * @return 是否请求成功
     */
    @Override
    public boolean updateSpace(SpaceUpdateRequest spaceUpdateRequest) {
        // 参数合法性校验
        ThrowUtils.throwIf(spaceUpdateRequest == null || spaceUpdateRequest.getId() <=0, ErrorCode.PARAMS_ERROR);
        // 将实体类和DTO类转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 校验修改合法性
        this.validateSpace(space,false);
        // 自动填充数据
        this.fillSpaceBySpaceLevel(space);
        // 判断是否存在
        Long id = space.getId();
        Space byId = this.getById(id);
        ThrowUtils.throwIf(byId==null , ErrorCode.NOT_FOUND_ERROR);
        // 根据id进行更新操作
        boolean b = this.updateById(space);
        if (!b) throw new BusinessException(ErrorCode.OPERATION_ERROR);
        return b;
    }

    /**
     * @param spaceEditRequest  空间编辑请求封装类
     * @param request 用于获取当前登录用户信息
     * @return 是否编辑成功
     */
    @Override
    public boolean editSpace(SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        // 健壮性判断
        ThrowUtils.throwIf(spaceEditRequest == null || spaceEditRequest.getId() <=0, ErrorCode.PARAMS_ERROR);
        // 由于spaceEditRequest只有id和spaceName两个字段,因此先查询,再去做后续的逻辑判断
        Space space = new Space();
        space = this.getById(spaceEditRequest.getId());
        // 将修改后的值赋值给space,用于后续逻辑判断
        // 将DTO类转换为实体类
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 修改时校验
        this.validateSpace(space,false);
        // 自动填充数据
        this.fillSpaceBySpaceLevel(space);
        // 判断是否存在
        Long id = space.getId();
        Space byId = this.getById(id);
        ThrowUtils.throwIf(byId==null , ErrorCode.NOT_FOUND_ERROR);
        // 判断是否为本人
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!Objects.equals(loginUser.getId(), byId.getUserId()), ErrorCode.NO_AUTH_ERROR);
        // 修改数据库操作
        boolean b = this.updateById(space);
        if (!b) throw new BusinessException(ErrorCode.OPERATION_ERROR);
        return b;
    }

    /**
     *
     * @param spaceAddRequest 创建空间请求封装类
     * @param request 用于获取当前登录用户
     * @return 创建成功的spaceId
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest==null, ErrorCode.PARAMS_ERROR);
        // 将DTO类转换为实体类
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 如果没有传入空间类型，则默认为私有空间
        if (space.getSpaceType() == null){
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 进行验证
        this.validateSpace(space,true);
        // 自动填充参数
        this.fillSpaceBySpaceLevel(space);
        // 获取当前登录用户id
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 进行权限校验
        // 普通用户只能创建普通版空间
        if (space.getSpaceLevel() != SpaceLevelEnum.COMMON.getValue() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 创建空间时,要进行加锁操作,这里使用分布式锁
        String redissonLock = "lock:user:" + userId;
        RLock userLock = redissonClient.getLock(redissonLock);
        boolean b = userLock.tryLock();
        if (!b) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"禁止重复创建个人空间");
        }
        // 获取锁成功,开始业务逻辑
        try{
            Long spaceId = transactionTemplate.execute(
                    status -> {
                        boolean exists =
                                this.lambdaQuery().eq(Space::getUserId, userId)
                                        .eq(Space::getSpaceType, space.getSpaceType())
                                        .exists();
                        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间只能创建一个");
                        // 写入数据库
                        boolean save = this.save(space);
                        ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);
                        // 如果是团队空间，关联新增团队成员记录
                        if (Objects.equals(SpaceTypeEnum.TEAM.getValue(), space.getSpaceType())) {
                            SpaceUser spaceUser = new SpaceUser();
                            spaceUser.setUserId(userId);
                            spaceUser.setSpaceId(space.getId());
                            // 创建者默认为团队空间的管理员
                            spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                            boolean result = spaceUserService.save(spaceUser);
                            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"创建团队成员记录失败");
                        }
                        // 返回新写入的数据id
                        return space.getId();
                    }
            );
        }finally {
            // 务必检查锁状态再释放，防止释放了别人的锁或抛出异常
            if (userLock.isLocked() && userLock.isHeldByCurrentThread()) {
                userLock.unlock();
            }
        }
        return Optional.ofNullable(space.getId()).orElse(-1L);
    }

    /**
     * 将Space打包为SpaceVO
     * @param space  待转换的Space信息
     * @param request 请求参数
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 健壮性判断
        ThrowUtils.throwIf(space == null || space.getId() <= 0, ErrorCode.PARAMS_ERROR);
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 获取对应的userId
        Long userId = space.getUserId();
        ThrowUtils.throwIf(userId==null , ErrorCode.PARAMS_ERROR);
        // 从数据库表中查询User
        User byId = userService.getById(userId);
        ThrowUtils.throwIf(byId==null , ErrorCode.NOT_FOUND_ERROR);
        // 转换为UserVo并插入到SpaceVO中
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(byId, userVO);
        spaceVO.setUser(userVO);
        return spaceVO;
    }

    /**
     * 分页查询Page
     * @param spaceQueryRequest 空间查询请求封装类
     * @param request           HttpServletRequest
     * @return
     */
    @Override
    public Page<Space> listSpacePage(SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {
        // 参数健壮性判断
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        int pageSize = spaceQueryRequest.getPageSize();
        int current = spaceQueryRequest.getCurrent();
        Long id = spaceQueryRequest.getId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Long userId = spaceQueryRequest.getUserId();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        // 拼接查询语句
//        QueryWrapper<Space> spaceQueryWrapper = new QueryWrapper<>();
//        spaceQueryWrapper.eq(id > 0,"id",id);
//        spaceQueryWrapper.like(spaceName != null,"spaceName",spaceName);
//        spaceQueryWrapper.eq(userId > 0 ,"userId",userId);
//        spaceQueryWrapper.eq(spaceLevel != null ,"spaceLevel",spaceLevel);

        // 使用 Lambda 条件构造器
        LambdaQueryWrapper<Space> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(spaceQueryRequest.getId() != null && spaceQueryRequest.getId() > 0, Space::getId, spaceQueryRequest.getId())
                .like(StrUtil.isNotBlank(spaceQueryRequest.getSpaceName()), Space::getSpaceName, spaceQueryRequest.getSpaceName())
                .eq(spaceQueryRequest.getUserId() != null && spaceQueryRequest.getUserId() > 0, Space::getUserId, spaceQueryRequest.getUserId())
                .eq(spaceQueryRequest.getSpaceLevel() != null, Space::getSpaceLevel, spaceQueryRequest.getSpaceLevel())
                .eq(spaceType != null, Space::getSpaceType, spaceType)
                .orderByDesc(Space::getCreateTime); // 建议加上默认排序
        // 进行查询操作
        Page<Space> spacePage = new Page<>(current, pageSize);
        return this.page(spacePage, queryWrapper);
    }

    /**
     *
     * @param spacePage 分页查询结果
     * @return
     */
    @Override
    public Page<SpaceVO> getListSpaceVO(Page<Space> spacePage) {
        // 获取分页好的数据
        List<Space> records = spacePage.getRecords();
        // 创建新的SpaceVO page
        Page<SpaceVO> spaceVOPage
                = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        // 如果分页查询结果为空直接返回即可
        if (CollUtil.isEmpty(records)) {
            return spaceVOPage;
        }
        // 将列表转换为SpaceVO
        List<SpaceVO> collect = records.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 获取所有UserId
        Set<Long> userIdSet = records.stream()
                .map(Space::getUserId).collect(Collectors.toSet());
        // 对应id存储user对象
        Map<Long, List<User>> userMap
                = userService.listByIds(userIdSet).stream().collect(Collectors.groupingBy(User::getId));
        collect.forEach(spaceVO -> {
            // 有可能用户注销
            User user = new User();
            Long userId = spaceVO.getUserId();
            if (userMap.containsKey(userId)) {
                user = userMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.convert2UserVO(user));
        });
        spaceVOPage.setRecords(collect);

        return spaceVOPage;
    }

    /**
     * 用于判断是不是User的space
     * @param loginUser
     * @param space
     */
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        ThrowUtils.throwIf(loginUser == null || space == null, ErrorCode.PARAMS_ERROR);
        // 获取space的userID
        Long userId = space.getUserId();
        if (!userId.equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"非本人用户空间");
        }
    }


}




