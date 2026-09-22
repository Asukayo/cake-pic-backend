package com.sharkycake.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.sharkycake.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.sharkycake.common.DeleteRequest;
import com.sharkycake.model.dto.picture.*;
import com.sharkycake.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sharkycake.model.entity.User;
import com.sharkycake.model.vo.PictureVO;
import org.apache.ibatis.annotations.Select;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author shark
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-12-21 20:52:04
*/
public interface PictureService extends IService<Picture> {


    /**
     * 上传文件,支持url+文件上传
     * @param inputSource 图片文件
     * @param pictureUploadRequest  包含图片id
     * @param loginUser  用于标记是哪个用户上传的
     * @return
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 获取图片封装的方法，可以为原有图片关联创建用户的信息
     * 在PictureVO中存储UserVO的信息
     * @param picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture picture,
                           HttpServletRequest request);

    /**
     * 分页获取图片封装
     * @param picturePage
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage,HttpServletRequest request);

    /**
     * 图片数据校验方法
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 将PicQueryWrapper转换为QueryWrapper的方法
     * @param pictureQueryRequest
     * @return
     */
    QueryWrapper<Picture> getPicQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 用于自动设置图片的审核状态
     * 如果是管理员上传则直接通过，普通用户上传则设置为待审核状态
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture,User loginUser);

    /**
     * 仅供管理员使用的用于批量抓取和创建页面的功能
     * @param pictureUploadByBatchRequest   包含searchText和搜索数两个关键词
     * @param loginUser 登录用户，用于图片信息关联用户
     * @return  成功创建数
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) throws InterruptedException;

    /**
     * 图片清理方法
     * @param picture   需要删除的图片对象
     */
    void clearPictureFile(Picture picture);


    /**
     * 供用户使用的图片修改接口
     * @param pictureEditRequest    图片修改请求封装类
     * @param request               用于获取user信息
     * @return                      是否修改成功
     */
    boolean editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request);

    /**
     * 按颜色查询图片
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    List<PictureVO> searchPictureByColor(Long spaceId,String picColor,User loginUser);

    /**
     * 批量修改图片服务
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * ai扩图服务
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);
}
