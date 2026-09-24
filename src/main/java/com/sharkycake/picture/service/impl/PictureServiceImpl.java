package com.sharkycake.picture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.sharkycake.infrastructure.api.aliyunai.AliYunAiApi;
import com.sharkycake.infrastructure.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.sharkycake.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.sharkycake.common.DeleteRequest;
import com.sharkycake.infrastructure.cos.CosConfig;
import com.sharkycake.space.constant.SpaceUserPermissionConstant;
import com.sharkycake.picture.constant.PictureTagCategory;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.infrastructure.cos.CosManager;

import com.sharkycake.space.auth.SpaceUserAuthManager;
import com.sharkycake.picture.upload.FilePictureUpload;
import com.sharkycake.picture.upload.PictureUploadTemplate;
import com.sharkycake.picture.upload.UrlPictureUpload;
import com.sharkycake.picture.dto.file.UploadPictureResult;
import com.sharkycake.picture.entity.Picture;
import com.sharkycake.space.entity.Space;
import com.sharkycake.user.entity.User;
import com.sharkycake.picture.enums.PictureReviewEnum;
import com.sharkycake.picture.vo.PictureVO;
import com.sharkycake.user.vo.UserVO;
import com.sharkycake.picture.service.PictureService;
import com.sharkycake.picture.mapper.PictureMapper;
import com.sharkycake.space.service.SpaceService;
import com.sharkycake.user.service.UserService;
import com.sharkycake.picture.util.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import com.sharkycake.picture.dto.CreatePictureOutPaintingTaskRequest;
import com.sharkycake.picture.dto.PictureEditByBatchRequest;
import com.sharkycake.picture.dto.PictureEditRequest;
import com.sharkycake.picture.dto.PictureQueryRequest;
import com.sharkycake.picture.dto.PictureReviewRequest;
import com.sharkycake.picture.dto.PictureUploadByBatchRequest;
import com.sharkycake.picture.dto.PictureUploadRequest;

/**
* @author shark
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-12-21 20:52:04
*/
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{



    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private UserService userService;

    @Resource
    private CosConfig  cosConfig;

    @Resource
    private ThreadPoolExecutor customExecutor;

    @Resource
    private AliYunAiApi aliYunAiApi;
    /**
     * url传图
     */
    @Resource
    private UrlPictureUpload urlPictureUpload;
    /**
     * 文件传图
     */
    @Resource
    private FilePictureUpload filePictureUpload;
    @Autowired
    private CosManager cosManager;
    @Autowired
    private SpaceService spaceService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 用户上传图片，若图片已存在则更新图片
     * 补充fillReviewParams参数
     * 补充检验Space的业务逻辑
     * @param inputSource 图片文件
     * @param pictureUploadRequest  包含图片id
     * @param loginUser  用于标记是哪个用户上传的
     * @return
     */
    @Override
    public PictureVO uploadPicture(Object inputSource,
                                   PictureUploadRequest pictureUploadRequest,
                                   User loginUser) {
        if (inputSource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片为空");
        }
        // 进行权限判断，判断是否为登录用户
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 校验空间是否存在,如果存在检验是否为其所有者,只有空间所有者才能上传私有图片
        Long spaceId = pictureUploadRequest == null ? null : pictureUploadRequest.getSpaceId();
//        spaceId不为null,即要上传到私有空间
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null,ErrorCode.PARAMS_ERROR,"该用户空间不存在");
            // 必须空间创始人才能够上传图片
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"不是该空间的创建者,无权限");
            }
            // 新增空间额度校验功能
            if(space.getTotalCount() > space.getMaxCount()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间条数不足");
            }
            if (space.getTotalSize() > space.getMaxSize()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间大小不足");
            }
        }

        // 判断上传图片是新增的还是更新的
        Long pictureId = null;
        Picture oldPicture = null;
        // 更新图片时，pictureUploadRequest用于指定图片唯一id
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果为更新（编辑）图片
        if (pictureId != null && pictureId > 0) {
//            boolean exists = this.lambdaQuery()
//                    .eq(Picture::getId, pictureId)
//                    .exists();
//            ThrowUtils.throwIf(!exists,ErrorCode.NOT_FOUND_ERROR,"图片不存在");
            oldPicture = this.getById(pictureId);
            // 如果未找到，直接抛出异常
            ThrowUtils.throwIf(ObjUtil.isNull(oldPicture),ErrorCode.NOT_FOUND_ERROR);
            // 仅管理员或者本人可以编辑图片,如果
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 如果此次没有上传spaceId,则使用原来图片的spaceId
            if (spaceId == null){
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            }else{
                // 如果上传了SpaceId,那么必须与原来的spaceId一致
                if(ObjUtil.notEqual(oldPicture.getSpaceId(),spaceId)){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"本次更新的空间id与原有的不一致");
                }
            }
        }
        // ------------开始正式上传逻辑---------------
        // 2.此时不论更新还是上传新图片都可以
        // 上传图片，得到信息，
        String uploadPathPrefix;
        if (spaceId == null) {
            // 按照用户id划分目录
            uploadPathPrefix = String.format("public/%s",loginUser.getId());
        }else {
            // 按照空间id划分目录
            uploadPathPrefix = String.format("space/%s",spaceId);
        }

        // 根据inputSource类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造用于存入数据库的图片信息
        Picture pic = new Picture();
        pic.setUrl(uploadPictureResult.getUrl());
        // 补充缩略图信息
        pic.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        pic.setOriginalKey(uploadPictureResult.getOriginalKey());
        pic.setCompressedKey(uploadPictureResult.getCompressedKey());
        pic.setThumbnailKey(uploadPictureResult.getThumbnailKey());
        pic.setStorageBucket(uploadPictureResult.getStorageBucket());
        // 修改图片名称,如果上传时制定了图片名则使用指定的图片名称
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        pic.setName(picName);
        pic.setPicSize(uploadPictureResult.getPicSize());
        pic.setPicWidth(uploadPictureResult.getPicWidth());
        pic.setPicHeight(uploadPictureResult.getPicHeight());
        pic.setPicScale(uploadPictureResult.getPicScale());
        pic.setPicFormat(uploadPictureResult.getPicFormat());
        pic.setPicColor(uploadPictureResult.getPicColor());

        String category = pictureUploadRequest == null ? null : pictureUploadRequest.getCategory();
        if (StrUtil.isBlank(category) && oldPicture != null) {
            category = oldPicture.getCategory();
        }
        pic.setCategory(StrUtil.isBlank(category) ? PictureTagCategory.DEFAULT_CATEGORY : category.trim());

        List<String> tags = pictureUploadRequest == null ? null : pictureUploadRequest.getTags();
        if (tags != null) {
            pic.setTags(JSONUtil.toJsonStr(tags));
        } else if (oldPicture != null && oldPicture.getTags() != null) {
            pic.setTags(oldPicture.getTags());
        } else {
            pic.setTags("[]");
        }

        pic.setUserId(loginUser.getId());
        // 插入spaceId到Picture对象中
        pic.setSpaceId(spaceId);
        // 如果pictureId不为空，表示更新，否则是新增图片
        if (pictureId != null) {
            // 如果是需要更新，需要补充id和编辑时间
            pic.setId(pictureId);
            pic.setEditTime(new Date());
        }
        // 新增fillReviewParams操作，无论是否为上传或者更新操作都设置审核状态
        this.fillReviewParams(pic, loginUser);

        Long finalSpaceId = spaceId;
        // 使用TransactionTemplate同时更新图片信息以及更新空间数据
        transactionTemplate.execute(status -> {
            // 先更新图片信息
            boolean b = this.saveOrUpdate(pic);
            ThrowUtils.throwIf(!b,ErrorCode.OPERATION_ERROR,"图片上传失败");
            // 如果spaceId不为空，则更新对应的空间信息
            if (finalSpaceId != null){
                Space space = spaceService.getById(finalSpaceId);
                space.setTotalCount(space.getTotalCount() + 1);
                space.setTotalSize(space.getTotalSize() + pic.getPicSize());
                boolean updateSpaceById = spaceService.updateById(space);
                ThrowUtils.throwIf(!updateSpaceById,ErrorCode.PARAMS_ERROR,"空间额度更新失败");
            }
            return null;
        });
        return PictureVO.objToVo(pic);
    }


    /**
     * 获取图片封装的方法，为原有的图片关联创建用户的信息
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 将对象转换为封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.convert2UserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 用户获取分页图片封装
     * @param picturePage
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        // 获取分页好的picture数据
        List<Picture> pictureList = picturePage.getRecords();
        // 获取当前picture分页的数据，并用来创建新的Page<PictureVO>
        Page<PictureVO> pictureVOPage =
                new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            // 如果没有查询到图片信息，直接返回即可
            return pictureVOPage;
        }
        // 将picture对象列表转换为封装类pictureVo列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1.查询关联用户信息,使用set避免重复
        Set<Long> collectedUserIds = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdListMap
                = userService.listByIds(collectedUserIds).stream().collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            // 获取对应的userId
            Long userId = pictureVO.getUserId();
            User user = null;
            // 从userIdListMap根据userId查找对应的user信息
            if(userIdListMap.containsKey(userId)) {
                user = userIdListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.convert2UserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 图片数据校验方法，用于更新和修改图片时进行判断
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        // 健壮性判断
        ThrowUtils.throwIf(picture == null,ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时,id不能为空,有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR,"id cannot be null");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() >1024, ErrorCode.PARAMS_ERROR,"url too long");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length()>800, ErrorCode.PARAMS_ERROR,"introduction too long");
        }
    }

    /**
     * 用于将pictureQueryRequest拼接为QueryWrapper的方法
     * 26.2.23 PictureQueryRequest新增spaceId和nullSpaceId
     * @param pictureQueryRequest
     * @return
     */
    public QueryWrapper<Picture> getPicQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 若没有传入queryWrapper,则直接返回null
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取出值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        // 是否只查询spaceId为null的数据?
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        // 获取分页属性
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 获取审核字段信息
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        // 从多字段中搜索
        if(StrUtil.isNotBlank(searchText)){
            // 需要拼接查询条件
            //sql逻辑中 and的优先级高于or。如果直接拼接而不是用这个特殊的and(Consumer)写法，逻辑会出错
            // 对应的sql语句为:WHERE ... AND (name LIKE '%值%' OR introduction LIKE '%值%')
            queryWrapper.and(qw ->qw.like("name",searchText)
                    .or()
                    .like("introduction",searchText));
        }
        queryWrapper.eq(id !=null && id > 0,"id",id);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(picFormat),"picFormat",picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category),"category",category);

        queryWrapper.eq(picWidth !=null && picWidth > 0,"picWidth",picWidth);
        queryWrapper.eq(picHeight !=null && picHeight > 0,"picHeight",picHeight);
        queryWrapper.eq(picScale !=null && picScale > 0,"picScale",picScale);
        queryWrapper.eq(picSize !=null && picSize > 0,"picSize",picSize);
        // 支持根据审核字段进行查询
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(reviewerId !=null && reviewerId > 0, "reviewerId", reviewerId);
        // 新增spaceId查询语句
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId,"spaceId");
        // 补充按编辑时间筛选的逻辑
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);

        // JSON数组查询
        if(CollUtil.isNotEmpty(tags)){
            for (String tag : tags) {
                // 假设记录 A: ["Java", "Python"],记录 B: ["JavaScript", "C++"]
                // 这里如果不加引号，生成的sql为：tags LIKE '%Java%',会把记录B也查出来
                // 加了引号就会生成sql：tags LIKE '%"Java"%'，就不会查到JavaScript
                // 但如果JSON中使用单引号连接就会出问题
                queryWrapper.like("tags","\""+tag+"\"");
            }
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            ThrowUtils.throwIf(!Arrays.asList("id", "name", "picSize", "picWidth", "picHeight",
                    "picScale", "createTime", "editTime", "updateTime", "reviewTime").contains(sortField),
                    ErrorCode.PARAMS_ERROR, "不支持的排序字段");
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
        } else {
            queryWrapper.orderByDesc("createTime", "id");
        }
        return queryWrapper;
    }


    /**
     * 图片审核,用于更新图片的审核状态的
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 获取图片id以及图片状态
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewEnum reviewStatusEnum = PictureReviewEnum.getEnumByValue(reviewStatus);
        // 审核结果不能为待审核状态
        if (id == null || reviewStatusEnum == null || PictureReviewEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断该图片是否存在
        Picture oldPic = this.getById(id);
        ThrowUtils.throwIf(oldPic == null,ErrorCode.NOT_FOUND_ERROR);
        // 避免重复审核,该图在数据库中已有审核结果，不能通过改为通过，不通过改为不通过
        if (oldPic.getReviewStatus().equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean updateRes = this.updateById(updatePicture);
        ThrowUtils.throwIf(!updateRes,ErrorCode.PARAMS_ERROR);
    }

    /**
     * 自动设置审核状态，每次上传或者编辑都会调用这个函数
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        // 判断null
        ThrowUtils.throwIf(picture == null,ErrorCode.PARAMS_ERROR);
        // 1.如果是管理员则自动过审
        if (userService.isAdmin(loginUser)) {
            // 审核人自动设置为管理员
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewStatus(PictureReviewEnum.PASS.getValue());
            picture.setReviewMessage("管理员自动过审");
        } else {
            // 2. 用户上传或编辑图片，都需要设置为待审核
            picture.setReviewStatus(PictureReviewEnum.REVIEWING.getValue());
        }

    }

    /**
     * 批量抓取和创建图片
     * @param pictureUploadByBatchRequest   包含searchText和搜索数两个关键词
     * @param loginUser 登录用户，用于图片信息关联用户
     * @return
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) throws InterruptedException {
        if (pictureUploadByBatchRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 解出搜索词，搜索条数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if(StrUtil.isBlank(namePrefix)){
            // 如果未给出namePrefix，则直接使用searchText作为名称前缀
            namePrefix = searchText;
        }

        Integer count = pictureUploadByBatchRequest.getCount();
        // 避免查询数过多导致ip封禁
        ThrowUtils.throwIf(count > 30,ErrorCode.PARAMS_ERROR,"爬取数最多为30");
        // 要进行抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try{
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取页面失败");
        }

        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取元素失败");
        }
        // 图片元素
//        Elements imgElementList = div.select("img.mimg");
        // 修改选择器，获取包含完整数据的元素
        Elements imgElementList = div.select(".iusc");
        int uploadCount = 0;

        for (Element imgElement : imgElementList) {
//            String fileUrl = imgElement.attr("src");
            // 1.获取data-m属性中的JSON字符串
            String dataM = imgElement.attr("m");
            String fileUrl;
            try{
                // 解析JSON字符串
                JSONObject jsonObject = JSONUtil.parseObj(dataM);
                // 获取murl字段（原始图片url）
                fileUrl = jsonObject.getStr("murl");
            }catch (Exception e){
                log.error("解析图片数据失败",e);
                continue;
            }
            // 2.开始上传
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过:{}",fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            if (StrUtil.isNotBlank(namePrefix)) {
                // 如果名称前缀不为空，则设置图片名
                pictureUploadRequest.setPicName(namePrefix+"_"+(uploadCount+1));
            }
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl,pictureUploadRequest,loginUser);
                log.info("图片上传成功，id:{}",pictureVO.getId());
                uploadCount++;
            }catch (Exception e){
                log.error("图片上传失败",e);
                continue;
            }
            if (uploadCount >= count){
                break;
            }
            Thread.sleep(1000);
        }
        return uploadCount;
    }

    /**
     * 异步删除图片文件方法
     * 传入的oldPicture在表中的isDelete字段必须为1
     * @param oldPicture   需要删除的图片对象
     */
//    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        ThrowUtils.throwIf(
                oldPicture == null || oldPicture.getId() == null,
                ErrorCode.PARAMS_ERROR);

        String bucket = oldPicture.getStorageBucket();
        // 历史记录缺少原图信息时，不猜测原图位置
        ThrowUtils.throwIf(
                StrUtil.isBlank(bucket)
                        || StrUtil.isBlank(oldPicture.getOriginalKey()),
                ErrorCode.OPERATION_ERROR,
                "图片缺少 COS 对象信息，需要补齐后再清理");
        // 兼容旧记录只保存 URL 的共享引用：
        // 如果仍有人使用这些展示地址，先保留整组文件
        Set<String> urls = new LinkedHashSet<>(
                Arrays.asList(
                        oldPicture.getUrl(),
                        oldPicture.getThumbnailUrl()));
        urls.removeIf(StrUtil::isBlank);
        if (!urls.isEmpty()) {
            boolean sharedByUrl = this.lambdaQuery()
                    .and(q -> q.in(Picture::getUrl, urls)
                            .or()
                            .in(Picture::getThumbnailUrl, urls))
                    .exists();

            if (sharedByUrl) {
                log.info(
                        "图片文件仍有有效 URL 引用，暂不清理，pictureId={}",
                        oldPicture.getId());
                return;
            }
        }
        // 去重：缩略图可能就是压缩图
        Set<String> keys = new LinkedHashSet<>(
                Arrays.asList(
                        oldPicture.getOriginalKey(),
                        oldPicture.getCompressedKey(),
                        oldPicture.getThumbnailKey()));
        keys.removeIf(StrUtil::isBlank);
        for (String key : keys) {
            // 检查其他有效图片是否仍引用同一桶里的同一个对象
            boolean referenced = this.lambdaQuery()
                    .eq(Picture::getStorageBucket, bucket)
                    .and(q -> q.eq(Picture::getOriginalKey, key)
                            .or()
                            .eq(Picture::getCompressedKey, key)
                            .or()
                            .eq(Picture::getThumbnailKey, key))
                    .exists();
            if (referenced) {
                log.info(
                        "COS 对象仍有有效引用，跳过清理，bucket={}, key={}",
                        bucket, key);
                continue;
            }
            // 使用保存的 Key，不再通过替换 URL 或后缀推导
            cosManager.deleteObject(bucket, key);
        }
    }

    /**
     *
     * @param pictureEditRequest    图片修改请求封装类
     * @param request               用于获取user信息
     * @return
     */
    @Override
    public boolean editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditRequest == null,ErrorCode.PARAMS_ERROR);
        // 在此处将实体类和DTO进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意要将tags标签从List转换为String
        if (pictureEditRequest.getTags() != null) {
            picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        }
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        Long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null,ErrorCode.NOT_FOUND_ERROR);
        // 校验修改操作是否合法
        // 由于edit方法已经使用saToken注释
//        this.checkIfCanOperate(oldPicture,loginUser);
        // 补充审核参数，对于用户修改的图片，默认设置为待审核状态
        this.fillReviewParams(picture,loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return result;
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下所有图片（必须有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }

        // 将目标颜色转为 Color 对象
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictures = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    // 提取图片主色调
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片放到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 越大越相似
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                // 取前 12 个
                .limit(12)
                .collect(Collectors.toList());

        // 转换为 PictureVO
        return sortedPictures.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0 || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(pictureIdList.stream().anyMatch(id -> id == null || id <= 0),
                ErrorCode.PARAMS_ERROR, "图片ID必须大于0");
        List<Long> distinctIds = pictureIdList.stream().distinct().collect(Collectors.toList());
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 3. 查询指定图片，仅选择需要的字段
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, distinctIds)
                .list();

        ThrowUtils.throwIf(pictureList.size() != distinctIds.size(), ErrorCode.NOT_FOUND_ERROR,
                "部分图片不存在或不属于该空间");
        // 按请求中的顺序编号，不依赖数据库返回顺序。
        Map<Long, Picture> picturesById = pictureList.stream()
                .collect(Collectors.toMap(Picture::getId, picture -> picture));
        pictureList = distinctIds.stream().map(picturesById::get).collect(Collectors.toList());
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (tags != null) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });

        // 5. 先生成名称，再一次性保存分类、标签和名称。
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 从数据库中获取图片信息和url信息，构造请求参数后调用api创建扩图任务。
     * 如果图片有空间id，则需要校验权限，直接复用以前的权限校验方法
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        ThrowUtils.throwIf(createPictureOutPaintingTaskRequest == null
                        || createPictureOutPaintingTaskRequest.getPictureId() == null
                        || createPictureOutPaintingTaskRequest.getPictureId() <= 0
                        || createPictureOutPaintingTaskRequest.getParameters() == null,
                ErrorCode.PARAMS_ERROR, "pictureId和parameters不能为空");
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 权限校验
        // 已使用sa-Token进行校验，可以省略编码式校验
//        checkIfCanOperate(picture,loginUser);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        taskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }



    /**
     * 如果要处理大量数据,使用线程池+分批+并发进行优化操作
     * 批量编辑图片分类和标签
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchEditPictureMetadata(PictureEditByBatchRequest request, Long spaceId, Long loginUserId) {
        // 参数校验
//        validateBatchEditRequest(request, spaceId, loginUserId);

        // 查询空间下的图片
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, request.getPictureIdList())
                .list();

        if (pictureList.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "指定的图片不存在或不属于该空间");
        }

        // 分批处理避免长事务
        int batchSize = 100;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < pictureList.size(); i += batchSize) {
            List<Picture> batch = pictureList.subList(i, Math.min(i + batchSize, pictureList.size()));

            // 异步处理每批数据
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                batch.forEach(picture -> {
                    // 编辑分类和标签
                    if (request.getCategory() != null) {
                        picture.setCategory(request.getCategory());
                    }
                    if (request.getTags() != null) {
                        picture.setTags(String.join(",", request.getTags()));
                    }
                });
                boolean result = this.updateBatchById(batch);
                if (!result) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量更新图片失败");
                }
            }, customExecutor);

            futures.add(future);
        }
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }


    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }


}




