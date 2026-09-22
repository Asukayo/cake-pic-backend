package com.sharkycake.myBatisTest;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sharkycake.model.entity.Picture;
import com.sharkycake.service.PictureService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class removeAllTestPic {

    @Resource
    private PictureService pictureService;


    @Test
    public void removeAllTest(){
        // test_pic_378
        boolean remove = pictureService.remove(new QueryWrapper<Picture>().
                likeRight("name", "test_pic_"));
        System.out.println(remove);


    }


}
