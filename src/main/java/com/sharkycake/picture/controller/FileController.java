package com.sharkycake.picture.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.sharkycake.user.auth.annotation.AuthCheck;
import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.user.constant.UserConstant;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.infrastructure.cos.CosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * 文件联调工具 HTTP 接口。
 * 业务响应使用 code/data/message；Long 响应字段以字符串传输。
 */
@Api(tags = "文件联调工具")
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private CosManager cosManager;

    /**
     * 上传测试文件到 COS。
     * 仅平台管理员。multipart/form-data 必填 file；返回测试对象路径，不创建图库记录。
     */
    @ApiOperation(value = "上传测试文件到 COS",
            notes = "仅平台管理员。multipart/form-data 必填 file；返回测试对象路径，不创建图库记录。")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {

        String fileName = multipartFile.getOriginalFilename();
        String filePath = String.format("/test/%s", fileName);
        File file = null;
        try{

            file = File.createTempFile(filePath,null);
            multipartFile.transferTo(file);
            cosManager.putObject(filePath,file);

            return ResultUtils.success(filePath);
        }catch (IOException e){
            log.error("file upload error,filePath = " + filePath,e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"上传失败");
        }finally {
            if (file != null){

                boolean delete = file.delete();
                if (!delete){
                    log.error("file delete error,filePath = {}", filePath);
                }
            }
        }
    }

    /**
     * 下载 COS 测试文件。
     * 仅平台管理员。查询参数 filePath 为对象路径；成功直接返回二进制流，不使用 BaseResponse 包装。
     */
    @ApiOperation(value = "下载 COS 测试文件",
            notes = "仅平台管理员。查询参数 filePath 为对象路径；成功直接返回二进制流，不使用 BaseResponse 包装。")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download/")
    public void testDownloadFile(String filePath, HttpServletResponse response) throws IOException {

        COSObjectInputStream cosObjectInputStream = null;
        try{

            COSObject object = cosManager.getObject(filePath);
            cosObjectInputStream = object.getObjectContent();

            byte[] byteArray = IOUtils.toByteArray(cosObjectInputStream);

            response.setContentType("application/octet-stream;charset=utf-8");
            response.setHeader("Content-Disposition","attachment;filename="+filePath);

            response.getOutputStream().write(byteArray);
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("download file error,filePath = " + filePath,e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"下载失败");
        }finally {
            if (cosObjectInputStream != null){
                cosObjectInputStream.close();
            }
        }
    }

}
