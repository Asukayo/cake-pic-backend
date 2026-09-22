package com.sharkycake.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sharkycake.annotation.AuthCheck;
import com.sharkycake.api.aliyunai.AliYunAiApi;
import com.sharkycake.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.sharkycake.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.sharkycake.api.imagesearch.ImageSearchApiFacade;
import com.sharkycake.api.imagesearch.model.ImageSearchResult;
import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.DeleteRequest;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.constant.PictureTagCategory;
import com.sharkycake.constant.SpaceUserPermissionConstant;
import com.sharkycake.constant.UserConstant;
import com.sharkycake.exception.BusinessException;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.manager.auth.StpKit;
import com.sharkycake.manager.auth.SpaceUserAuthManager;
import com.sharkycake.manager.auth.annotation.SaSpaceCheckPermission;
import com.sharkycake.model.dto.picture.*;
import com.sharkycake.model.entity.Picture;
import com.sharkycake.model.entity.PictureCleanupTask;
import com.sharkycake.model.entity.Space;
import com.sharkycake.model.entity.User;
import com.sharkycake.model.enums.PictureReviewEnum;
import com.sharkycake.model.vo.PictureVO;
import com.sharkycake.model.vo.PictureCleanupTaskVO;
import io.swagger.annotations.ApiParam;
import com.sharkycake.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 图片与任务 HTTP 接口。
 * 业务响应使用 code/data/message；Long 响应字段以字符串传输。
 */
@Api(tags = "图片与任务")
@RestController
@RequestMapping("/pic")
@Slf4j
public class PictureController {

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private PictureDeleteService pictureDeleteService;

    @Resource
    private PictureCleanupTaskService pictureCleanupTaskService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 管理员查询清理任务状态。
     * 仅平台管理员，并刷新数据库身份。eventId 非空必填、最长64字符；返回任务管理视图，不返回存储桶或对象key，不触发清理。
     */
    @ApiOperation(value = "管理员查询清理任务状态",
            notes = "仅平台管理员，并刷新数据库身份。eventId 非空必填、最长64字符；返回任务管理视图，不返回存储桶或对象key，不触发清理。")
    @GetMapping("/cleanup/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureCleanupTaskVO> getCleanupTask(
            @ApiParam(value = "任务事件 ID，非空且最长 64 字符", required = true)
            @RequestParam("eventId") String eventId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureCleanupTaskService.getTaskView(eventId, loginUser));
    }

    /**
     * 管理员分页查询清理任务。
     * 仅平台管理员，并刷新数据库身份。请求体必填，可传{}；current>=1，pageSize=1到100，默认1/10。可按 eventId、pictureId、taskStatus=0到5精确过滤，固定按创建时间和ID倒序。
     */
    @ApiOperation(value = "管理员分页查询清理任务",
            notes = "仅平台管理员，并刷新数据库身份。请求体必填，可传{}；current>=1，pageSize=1到100，默认1/10。可按 eventId、pictureId、taskStatus=0到5精确过滤，固定按创建时间和ID倒序。")
    @PostMapping("/cleanup/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<PictureCleanupTaskVO>> listCleanupTasks(
            @RequestBody PictureCleanupTaskQueryRequest query, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureCleanupTaskService.listTaskViews(query, loginUser));
    }

    private final Cache<String,String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)

                    .expireAfterWrite(5L, TimeUnit.MINUTES).build();
    @Autowired
    private SpaceService spaceService;

    /**
     * 查询图片标签与分类选项。
     * 无需登录，无业务参数。返回配置中的标签和分类选项，不是当前数据库的统计结果。
     */
    @ApiOperation(value = "查询图片标签与分类选项",
            notes = "无需登录，无业务参数。返回配置中的标签和分类选项，不是当前数据库的统计结果。")
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> tagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();

        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 使用已有图片进行外部以图搜图。
     * JSON 必填 pictureId>0，图片必须存在；返回外部搜索的 thumbUrl/fromUrl 列表，无分页或保证条数。沿用图片详情的审核状态和空间访问权限校验。
     */
    @ApiOperation(value = "使用已有图片进行外部以图搜图",
            notes = "JSON 必填 pictureId>0，图片必须存在；返回外部搜索的 thumbUrl/fromUrl 列表，无分页或保证条数。沿用图片详情的审核状态和空间访问权限校验。")
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest,
                                                                       HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        PictureVO picture = getPictureVOById(pictureId, request).getData();
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(picture.getUrl());
        return ResultUtils.success(resultList);
    }

    /**
     * 在自己的空间按颜色搜图。
     * 必须登录且为空间创建者。必填 spaceId、非空 picColor，建议 #RRGGBB；按相似度返回最多12张 PictureVO，无分页或得分字段。
     */
    @ApiOperation(value = "在自己的空间按颜色搜图",
            notes = "必须登录且为空间创建者。必填 spaceId、非空 picColor，建议 #RRGGBB；按相似度返回最多12张 PictureVO，无分页或得分字段。")
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> result = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 上传文件或替换原图。
     * 需要 picture:upload。multipart/form-data 必填 file，文件不超过20MiB，支持jpg/jpeg/png/gif/bmp；可选 id、spaceId、picName。指定空间还须所有者，替换还须上传者或平台管理员。
     */
    @ApiOperation(value = "上传文件或替换原图",
            notes = "需要 picture:upload。multipart/form-data 必填 file，文件不超过20MiB，支持jpg/jpeg/png/gif/bmp；可选 id、spaceId、picName。指定空间还须所有者，替换还须上传者或平台管理员。")
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)

    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile file,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService
                .uploadPicture(file,pictureUploadRequest,loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过 URL 上传或替换图片。
     * 需要 picture:upload。JSON 必填非空 HTTP(S) fileUrl；可选 id、spaceId、picName。指定空间还须所有者，替换还须上传者或平台管理员，spaceId不可跨空间变更。
     */
    @ApiOperation(value = "通过 URL 上传或替换图片",
            notes = "需要 picture:upload。JSON 必填非空 HTTP(S) fileUrl；可选 id、spaceId、picName。指定空间还须所有者，替换还须上传者或平台管理员，spaceId不可跨空间变更。")
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request
    ){
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService
                .uploadPicture(fileUrl,pictureUploadRequest,loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 管理员审核图片。
     * 仅平台管理员。JSON 必填 id、reviewStatus（1通过或2拒绝），可选 reviewMessage；更新审核人和审核时间。
     */
    @ApiOperation(value = "管理员审核图片",
            notes = "仅平台管理员。JSON 必填 id、reviewStatus（1通过或2拒绝），可选 reviewMessage；更新审核人和审核时间。")
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewPicture(@RequestBody PictureReviewRequest pictureReviewRequest,
                                               HttpServletRequest request) {

        ThrowUtils.throwIf(pictureReviewRequest == null,ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 删除图片并创建文件清理任务。
     * 需要 picture:delete，JSON 必填 id>0；Service 再校验实际资源权限。true 表示逻辑删除、额度扣减和任务/Outbox事务成功，不代表COS文件已清理。
     */
    @ApiOperation(value = "删除图片并创建文件清理任务",
            notes = "需要 picture:delete，JSON 必填 id>0；Service 再校验实际资源权限。true 表示逻辑删除、额度扣减和任务/Outbox事务成功，不代表COS文件已清理。")
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest request) {

        ThrowUtils.throwIf(
                deleteRequest == null
                        || deleteRequest.getId() == null
                        || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR
        );

        User loginUser = userService.getLoginUser(request);

        pictureDeleteService.deletePicture(
                deleteRequest.getId(),
                loginUser.getId()
        );

        return ResultUtils.success(true);
    }

    /**
     * 管理员手动重试文件清理。
     * 仅平台管理员，并刷新数据库身份。查询参数 eventId 必填；复用既有清理流程，不再次扣减空间额度。data 为调用结束后的任务状态0到5，不是布尔值。
     */
    @ApiOperation(value = "管理员手动重试文件清理",
            notes = "仅平台管理员，并刷新数据库身份。查询参数 eventId 必填；复用既有清理流程，不再次扣减空间额度。data 为调用结束后的任务状态0到5，不是布尔值。")
    @PostMapping("/cleanup/retry")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> retryCleanup(
            @RequestParam String eventId,
            HttpServletRequest request) {

        User loginUser = userService.getLoginUser(request);
        User currentUser = userService.getById(loginUser.getId());
        ThrowUtils.throwIf(
                currentUser == null || !userService.isAdmin(currentUser),
                ErrorCode.NO_AUTH_ERROR
        );
        pictureCleanupTaskService.retryTask(eventId);
        PictureCleanupTask task = pictureCleanupTaskService.lambdaQuery()
                .eq(PictureCleanupTask::getEventId, eventId)
                .one();

        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(task.getTaskStatus());
    }

    /**
     * 管理员更新图片信息。
     * 仅平台管理员。JSON 必填 id>0，可选名称、简介、分类、标签等；简介最多800字符，更新时填充管理员审核信息。
     */
    @ApiOperation(value = "管理员更新图片信息",
            notes = "仅平台管理员。JSON 必填 id>0，可选名称、简介、分类、标签等；简介最多800字符，更新时填充管理员审核信息。")
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,
                                               HttpServletRequest request) {

        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);

        if (pictureUpdateRequest.getTags() != null) {
            picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        }

        pictureService.validPicture(picture);

        Long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null,ErrorCode.NOT_FOUND_ERROR);

        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(picture,loginUser);

        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员查询图片实体。
     * 仅平台管理员。查询参数 id 必填且大于0；返回 Picture 实体，包含审核和存储元数据，普通页面使用 /get/vo。
     */
    @ApiOperation(value = "管理员查询图片实体",
            notes = "仅平台管理员。查询参数 id 必填且大于0；返回 Picture 实体，包含审核和存储元数据，普通页面使用 /get/vo。")
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(@RequestParam("id") Long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <=0,ErrorCode.PARAMS_ERROR);

        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null,ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }

    /**
     * 查询已过审图片详情。
     * 查询参数 id 必填且大于0。所有范围都要求 reviewStatus=1；空间内图片还需 picture:view。返回 PictureVO 及上传者展示信息。
     */
    @ApiOperation(value = "查询已过审图片详情",
            notes = "查询参数 id 必填且大于0。所有范围都要求 reviewStatus=1；空间内图片还需 picture:view。返回 PictureVO 及上传者展示信息。")
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(@RequestParam("id") Long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <=0,ErrorCode.PARAMS_ERROR);

        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null,ErrorCode.NOT_FOUND_ERROR);

        if(!Objects.equals(picture.getReviewStatus(), PictureReviewEnum.PASS.getValue())){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        if (picture.getSpaceId() != null){
            checkSpaceViewPermission(picture.getSpaceId().toString(), request);
        }
        return ResultUtils.success(pictureService.getPictureVO(picture,request));
    }

    /**
     * 管理员分页查询图片实体。
     * 仅平台管理员。请求体必填，默认 current=1/pageSize=10；返回 Page<Picture>，不使用普通VO接口的19条上限。
     */
    @ApiOperation(value = "管理员分页查询图片实体",
            notes = "仅平台管理员。请求体必填，默认 current=1/pageSize=10；返回 Page<Picture>，不使用普通VO接口的19条上限。")
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest){

        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        Page<Picture> picturePage =
                pictureService.page(new Page<>(current, pageSize), pictureService.getPicQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页搜索图片展示信息。
     * 请求体必填，可传{}；默认current=1/pageSize=10，pageSize必须小于20。省略spaceId只查公共已过审图片；指定空间需picture:view。tags为AND匹配，编辑时间范围左闭右开，详见联调补充。
     */
    @ApiOperation(value = "分页搜索图片展示信息",
            notes = "请求体必填，可传{}；默认current=1/pageSize=10，pageSize必须小于20。省略spaceId只查公共已过审图片；指定空间需picture:view。tags为AND匹配，编辑时间范围左闭右开，详见联调补充。")
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,HttpServletRequest request){
        validatePicturePage(pictureQueryRequest);
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        String spaceId = pictureQueryRequest.getSpaceId();

        ThrowUtils.throwIf(pageSize >= 20,ErrorCode.PARAMS_ERROR);

        if (StrUtil.isBlank(spaceId)) {

            pictureQueryRequest.setSpaceId(null);
            pictureQueryRequest.setReviewStatus(PictureReviewEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        }else{

            checkSpaceViewPermission(spaceId, request);
            pictureQueryRequest.setNullSpaceId(false);
        }

        Page<Picture> picturePage =
                pictureService.page(new Page<>(current, pageSize), pictureService.getPicQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage,request));
    }

    @ApiOperation(value = "分页查询我上传的公共图片",
            notes = "必须登录；请求体可传{}，pageSize为1到19。仅查询当前用户上传的公共图片，包含待审核和被拒绝图片，可按reviewStatus筛选。userId和空间范围由服务端确定。")
    @PostMapping("/list/my")
    public BaseResponse<Page<PictureVO>> listMyPictures(@RequestBody PictureQueryRequest query,
                                                       HttpServletRequest request) {
        validatePicturePage(query);
        User loginUser = userService.getLoginUser(request);
        query.setUserId(loginUser.getId());
        query.setSpaceId(null);
        query.setNullSpaceId(true);
        Page<Picture> page = pictureService.page(new Page<>(query.getCurrent(), query.getPageSize()),
                pictureService.getPicQueryWrapper(query));
        return ResultUtils.success(pictureService.getPictureVOPage(page, request));
    }

    private void validatePicturePage(PictureQueryRequest query) {
        ThrowUtils.throwIf(query == null || query.getCurrent() < 1
                || query.getPageSize() < 1 || query.getPageSize() >= 20,
                ErrorCode.PARAMS_ERROR, "current必须大于0，pageSize必须为1到19");
    }

    private void checkSpaceViewPermission(String spaceId, HttpServletRequest request) {
        long id;
        try {
            id = Long.parseLong(spaceId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "spaceId必须是正整数");
        }
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "spaceId必须是正整数");
        User loginUser = userService.getLoginUser(request);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(!spaceUserAuthManager.getPermissionList(space, loginUser)
                .contains(SpaceUserPermissionConstant.PICTURE_VIEW), ErrorCode.NO_AUTH_ERROR);
    }

    /**
     * 通过缓存查询图片列表。
     * 请求体必填，current>=1，pageSize为1到19。公共图库强制已过审并使用两级缓存；指定spaceId时校验空间权限并直接查询数据库。
     */
    @ApiOperation(value = "通过缓存查询图片列表",
            notes = "请求体必填，current>=1，pageSize为1到19。公共图库强制已过审并使用两级缓存；指定spaceId时校验空间权限并直接查询数据库。")
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageCache
            (@RequestBody PictureQueryRequest pictureQueryRequest,HttpServletRequest request){
        validatePicturePage(pictureQueryRequest);
        // 空间图片走实时权限校验和查询；共享缓存只保存公共图库。
        if (StrUtil.isNotBlank(pictureQueryRequest.getSpaceId())) {
            return listPictureVOByPage(pictureQueryRequest, request);
        }
        pictureQueryRequest.setSpaceId(null);
        pictureQueryRequest.setNullSpaceId(true);
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        ThrowUtils.throwIf(pageSize >= 20,ErrorCode.PARAMS_ERROR);

        pictureQueryRequest.setReviewStatus(PictureReviewEnum.PASS.getValue());

        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String redisKey = "cake-pic:publicPicturePage:v2:" + hashKey;

        String cachedKey = "publicPicturePage:v2:" + hashKey;
        String cachedValue = LOCAL_CACHE.getIfPresent(cachedKey);
        if (cachedValue != null){
            Page<PictureVO> caffeineCachedPictureVO = JSONUtil.toBean(cachedValue, Page.class);
            log.info("Caffeine命中成功,,key为:{}",cachedKey);
            return ResultUtils.success(caffeineCachedPictureVO);
        }

        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        String cachedPictureVo = ops.get(redisKey);
        if(StrUtil.isNotBlank(cachedPictureVo)){
            Page<PictureVO> pictureVOPage = JSONUtil.toBean(cachedPictureVo, Page.class, true);

            LOCAL_CACHE.put(cachedKey,cachedPictureVo);
            return ResultUtils.success(pictureVOPage);
        }

        Page<Picture> picturePage
                = pictureService.page(new Page<>(current, pageSize),
                pictureService.getPicQueryWrapper(pictureQueryRequest));

        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        cachedPictureVo = JSONUtil.toJsonStr(pictureVOPage);

        int cachedExpireTime = 300 + RandomUtil.randomInt(0,300);
        ops.set(redisKey,cachedPictureVo,cachedExpireTime,TimeUnit.SECONDS);

        LOCAL_CACHE.put(cachedKey,cachedPictureVo);

        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片名称和描述。
     * 需要 picture:edit。JSON 必填 id>0，可选 name/introduction/category/tags；简介最多800字符，编辑后按当前用户的平台身份重新设置审核状态。
     */
    @ApiOperation(value = "编辑图片名称和描述",
            notes = "需要 picture:edit。JSON 必填 id>0，可选 name/introduction/category/tags；简介最多800字符，编辑后按当前用户的平台身份重新设置审核状态。")
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest,
                                             HttpServletRequest request) {

        if (pictureEditRequest == null || pictureEditRequest.getId() == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean editResult = pictureService.editPicture(pictureEditRequest,request);

        return ResultUtils.success(editResult);
    }

    /**
     * 管理员批量抓图并上传。
     * 仅平台管理员。请求体必填，前端应传非空searchText，count默认10且后端上限30，前端限制1到30。名称为namePrefix_序号，空前缀使用searchText；同步执行，data为实际成功张数。
     */
    @ApiOperation(value = "管理员批量抓图并上传",
            notes = "仅平台管理员。请求体必填，前端应传非空searchText，count默认10且后端上限30，前端限制1到30。名称为namePrefix_序号，空前缀使用searchText；同步执行，data为实际成功张数。")
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) throws InterruptedException {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    /**
     * 批量编辑空间图片。
     * 需要 picture:edit 且为空间所有者。必填spaceId、非空pictureIdList；可选category/tags/nameRule。按pictureIdList顺序生成名称后统一保存；tags=[]清空标签，省略则保留。部分图片不存在或不属于该空间时返回错误。
     */
    @ApiOperation(value = "批量编辑空间图片",
            notes = "需要 picture:edit 且为空间所有者。必填spaceId、非空pictureIdList；可选category/tags/nameRule。按pictureIdList顺序生成名称后统一保存；tags=[]清空标签，省略则保留。部分图片不存在或不属于该空间时返回错误。")
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 创建 AI 扩图任务。
     * 需要 picture:edit 且必须登录。JSON 必填正整数pictureId及parameters参数对象；字段使用camelCase。上游模型为image-out-painting，创建仅返回taskId/status，不保存图片。
     */
    @ApiOperation(value = "创建 AI 扩图任务",
            notes = "需要 picture:edit 且必须登录。JSON 必填正整数pictureId及parameters参数对象；字段使用camelCase。上游模型为image-out-painting，创建仅返回taskId/status，不保存图片。")
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务。
     * 查询参数taskId非空必填。当前未校验任务归属；先判断外层code，再判断output.taskStatus。成功后从outputImageUrl预览，保存须另调/upload/url。
     */
    @ApiOperation(value = "查询 AI 扩图任务",
            notes = "查询参数taskId非空必填。当前未校验任务归属；先判断外层code，再判断output.taskStatus。成功后从outputImageUrl预览，保存须另调/upload/url。")
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(
            @ApiParam(value = "创建扩图任务返回的 taskId，不能为空", required = true)
            @RequestParam("taskId") String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }

}
